;; TODO: Automatically include code blocks from within the same answer
;; TODO: Allow changing the language manually
;; TODO: Keyboard shortcuts
(ns com.mjdowney.rendergpt
  (:require [clojure.string :as string]
            [crate.core :as crate]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            ["react-multi-select-component" :refer (MultiSelect)]
            ["plantuml-encoder" :as plantuml-encoder]
            [re-highlight.core :refer [highlight]]))

;;; (1) Helpers to scan the page for code blocks in ChatGPT responses, read
;;; their source code, and add elements to their title bars.

(defn get-gpt-response-code-blocks []
  (js/document.getElementsByClassName "bg-black mb-4 rounded-md"))

(defn code-block-type [code-block-ele]
  (some-> (.getElementsByTagName code-block-ele "span") first .-innerText))

(defn set-code-block-type! [code-block-ele type]
  (when-let [e (some-> (.getElementsByTagName code-block-ele "span") first)]
    (set! (.-innerText e) type)))

(defn code-block-source [code-block-ele]
  (some-> (.getElementsByTagName code-block-ele "code") first .-innerText))

(defn prompt-for-code-block
  "Given ChatGPT's response code, get the text of the preceding user prompt."
  [code-block-ele]
  (let [chat-parent-sel "flex flex-col text-sm dark:bg-gray-800"
        chat-parent (first (js/document.getElementsByClassName chat-parent-sel))
        user-msgs (->> (.-children chat-parent)
                       ; filter out the model selection element at the top of
                       ; the chat area
                       (remove #(string/starts-with? (.-className %) "flex"))
                       (take-nth 2)
                       reverse)]
    (when-let [msg
               (first
                 (drop-while
                   (fn [user-chat]
                     ; Drop while the code block is not after the user msg
                     (= (.compareDocumentPosition code-block-ele user-chat)
                        (.-DOCUMENT_POSITION_FOLLOWING js/Node)))
                   user-msgs))]
      (.-textContent msg))))

(defn add-to-code-block-title-bar! [code-block-ele new-ele]
  (if-let [existing-button (first (.getElementsByTagName code-block-ele "button"))]
    (.insertBefore (.-parentNode existing-button) new-ele existing-button)
    (.appendChild (.-parentElement code-block-ele) new-ele)))

;;; (2) A React component replacing the code block contents with an iframe for
;;; displaying the result of evaling / rendering the code.

(def all-code-blocks
  "All code blocks on the page, in order, with their types and source code."
  (r/atom [#_{:code "<div ...>" :type "html" :desc "user message to chatgpt"}]))

(defn code-selection-options [initial-selection-idx all-code-blocks]
  (into []
    (map-indexed
      (fn [idx {:keys [type desc]}]
        {:label (str type " @" (if (= idx initial-selection-idx) "here" idx))
         :desc desc
         :value idx}))
    all-code-blocks))

(defn selection-option-renderer
  "Customize the dropdown component for selecting code blocks to include to
  also display a preview of the prompt that produced each of the code blocks."
  [props]
  (let [{:keys [checked option onClick disabled]} (js->clj props :keywordize-keys true)]
    (r/as-element
      [:div {:class (str "item-renderer" (if disabled " disabled" ""))}
       [:input {:type "checkbox"
                :checked checked
                :disabled disabled
                :onChange (fn [e] (onClick e))
                :tabIndex -1}]
       [:div.code-selection {:style {:display "grid" :padding 0 :margin 0}}
        [:span (:label option)]
        [:span.code-selection-desc
         (let [d (:desc option)]
           (if (> (count d) 500)
             (str (subs d 0 500) "...")
             d))]]])))

(defn filter-options
  "Filter the options in a multi-select dropdown for search term inclusion in
  either the :label or the :desc."
  [options search-term]
  (if (seq search-term)
    (let [search-term (string/lower-case search-term)]
      (.filter options
        (fn [option]
          (or
            (string/includes? (.-label ^js option) search-term)
            (string/includes?
              (string/lower-case (.-desc ^js option))
              search-term)))))
    options))

(defn sources-dropdown
  "A multi-select dropdown for selecting which of ChatGPT's code sources to
  include, beyond the selected code block."
  [initial-selection-idx selected]
  (let [options (code-selection-options initial-selection-idx @all-code-blocks)]
    [:> MultiSelect
     {:options (clj->js (reverse options))
      :overrideStrings #js {:selectSomeItems "Sources..."
                            :allItemsAreSelected "All sources selected"}
      :filterOptions filter-options
      :ItemRenderer selection-option-renderer
      :className "dark"
      :hasSelectAll false
      :disableSearch false
      :onChange (fn [e]
                  (js/console.log "Selected:" e)
                  (reset! selected (mapv #(.-value %) e)))
      :value (mapv #(nth options %) @selected)}]))

(defn settings-dropdown [option->active option->desc]
  (let [options (mapv
                  (fn [[option active]]
                    {:label (name option)
                     :desc (get option->desc option "")
                     :value option
                     :active active})
                  @option->active)]
    [:> MultiSelect
     {:options         (clj->js options)
      :overrideStrings #js {:selectSomeItems     "Settings..."
                            :allItemsAreSelected "Settings..."}
      :ClearSelectedIcon nil
      :ItemRenderer    selection-option-renderer
      :valueRenderer   (fn [_options] "Settings...")
      :className       "dark"
      :hasSelectAll    false
      :disableSearch   true
      :onChange        (fn [e]
                         (swap! option->active
                           (fn [old]
                             (merge
                               (update-vals old (fn [_] false))
                               (zipmap
                                 (map #(keyword (.-value %)) e)
                                 (repeat true))))))
      :value           (filter :active options)}]))

(def settings-descriptions
  {:show-source "Show computed source instead of rendering."
   :order-by-type "Put CSS sources first, then HTML, then JS."})

(def default-settings
  (atom
    (-> (zipmap (keys settings-descriptions) (repeat true))
        (assoc :show-source false))))

(defn watch-and-update-defaults [settings]
  (add-watch settings :update-defaults
    (fn [_key _ref _old-state new-state]
      (reset! default-settings new-state))))

(defn settings-atom []
  (let [settings (r/atom @default-settings)]
    (watch-and-update-defaults settings)
    settings))

(defn code-block-html [{:keys [code type idx]}]
  (let [prefix (str "<!-- Code block at index " idx " -->\n")]
    (str prefix
      (case type
        "html" code
        "javascript" (if (string/starts-with? (string/trim code) "<script>")
                       code
                       (str "<script>\n" code "\n</script>"))
        "css" (if (string/starts-with? (string/trim code) "<style>")
                code
                (str "<style>\n" code "\n</style>"))

        ; if the code type is unknown, just embed it directly
        code))))

(defn build-source [selected all-code-blocks settings]
  (let [sort-order (fn [{:keys [type]}]
                     (get {"css" 0 "html" 1 "html+" 1 "javascript" 2} type 3))
        blocks (as-> selected $
                     (map (fn [idx] (assoc (nth all-code-blocks idx) :idx idx)) $)
                     (if (:order-by-type settings) (sort-by sort-order $) $))]
    (if (and (seq blocks) (every? #{"plantuml+" "plantuml"} (map :type blocks)))
      [:uml (string/join "\n" (map :code blocks))]
      [:html
       (->> blocks
            (map code-block-html)
            (string/join "\n\n"))])))

(defn rendergpt [code-block-idx]
  (let [selected (r/atom [code-block-idx])
        select-tt (r/atom false)
        settings-tt (r/atom false)
        settings (settings-atom)]
    (fn [code-block-idx]
      [:div.p4.overflow-y-auto.font-sans
       {:style (if (:show-source @settings) {} {:min-height "300px"})}
       [:div {:style {:background "#343540" :display "flex"}}
        [:div.dropdown-container
         {:onMouseOver (fn [_e] (reset! select-tt true))
          :onMouseOut (fn [_e] (reset! select-tt false))}
         [:span.tooltip {:style {:display (if @select-tt "block" "none")}}
          "Select which code blocks from ChatGPT's responses to render."]
         [sources-dropdown code-block-idx selected]]

        [:div.dropdown-container
         {:onMouseOver (fn [_e] (reset! settings-tt true))
          :onMouseOut (fn [_e] (reset! settings-tt false))}
         [:span.tooltip
          {:style {:display (if @settings-tt "block" "none")
                   :transform "translateX(-25%)"}}
          "Configuration for how to render the code blocks."]
         [settings-dropdown settings settings-descriptions]]]

       (let [settings @settings
             [src-type src] (build-source @selected @all-code-blocks settings)]
         (if (:show-source settings)
           [:div {:style {:font-size "1.1em"
                          :font-family "monospace !important"
                          :padding "20px"
                          :min-height "300px"}}
            [highlight {:language "html"} src]
            [:button.copy-code
             {:on-click (fn [_e] (js/navigator.clipboard.writeText src))}
             [:div.checkmark "✓"]
             "Copy combined code"]]
           (if (= src-type :uml)
             [:img
              {:src (str "https://www.plantuml.com/plantuml/img/" (plantuml-encoder/encode src))
               :style {:width "-webkit-fill-available"
                       :height "-webkit-fill-available"
                       :margin 0
                       :padding 0}}]
             [:iframe
              {:srcDoc src
               :style {:position         "relative"
                       :width            "100%"
                       :min-height       "500px"
                       :border           "1px solid steelblue"
                       :border-radius    "5px"
                       :background-color "white"}}])))])))

;;; (3) Vanilla JS to add a button element to each code block which injects the
;;; above react component.

(defn create-rendergpt-ele [parent idx]
  (let [container (crate/html [:div.rendergpt {:style {:display "none"}}])]
    (.appendChild parent container)
    (rdom/render [rendergpt idx] container)
    container))

(defn toggle-render-fn [ele idx render-button]
  (let [rendered? (atom false)
        hide! (fn [e] (set! (-> e .-style .-display) "none"))
        show! (fn [e] (set! (-> e .-style .-display) "block"))]
    (fn [_e]
      (let [children (.-children ele)
            content (aget children 1)
            rgpt (if (>= (.-length children) 3)
                   (aget children 2)
                   (create-rendergpt-ele ele idx))]
        (if @rendered?
          (do
            (hide! rgpt)
            (show! content)
            (set! (.-innerText render-button) "Render"))
          (do
            (hide! content)
            (show! rgpt)
            (set! (.-innerText render-button) "Source")))

        (reset! rendered? (not @rendered?))))))

(defn on-mutation [_mutation-records _observer]
  (let [n-registered (count @all-code-blocks)
        blocks (get-gpt-response-code-blocks)]

    ; Assume the chat window has been changed because the first chat no longer
    ; matches
    (when-let [b (first blocks)]
      (when-not (= (code-block-source b) (:code (first @all-code-blocks)))
        (reset! all-code-blocks [])))

    (doseq [[idx ele] (map-indexed vector blocks)]

      (let [t (string/lower-case (or (code-block-type ele) ""))
            t (case t
                ("html" "php" "svg" "xml") "html"
                ("js" "javascript") "javascript"
                ("css" "scss" "sass") "css"
                ("uml" "plantuml") "plantuml"
                t)
            attrs {:code (code-block-source ele)
                   :type t
                   :desc (prompt-for-code-block ele)}]

        (cond
          (>= idx n-registered)
          (swap! all-code-blocks conj attrs)

          ; If the most recent block has changed, update it
          (and (= idx (dec n-registered)) (not= attrs (peek @all-code-blocks)))
          (swap! all-code-blocks assoc idx attrs))

        (when (or (= t "html") (= t "plantuml"))
          (set-code-block-type! ele (str t "+"))
          (let [render-button (crate/html [:button.flex.ml-auto.gap-2 "Render"])
                toggle-render (toggle-render-fn ele idx render-button)]
            (.addEventListener render-button "click" toggle-render)
            (add-to-code-block-title-bar! ele render-button)))))))

(defn register-on-mutation [f]
  (let [observer (js/MutationObserver. f)]
    (.observe observer
      (.-body js/document)
      #js {:childList true :subtree true})))

(defn inject-stylesheet [href]
  (.appendChild js/document.head
    (crate/html
      [:link {:rel "stylesheet" :type "text/css" :href href}])))

(defn ^:export init []
  (inject-stylesheet (js/chrome.runtime.getURL "rendergpt.css"))
  (register-on-mutation on-mutation)
  (js/console.log "injected rendergpt"))

(comment
  ; Example prompt:
  ; Create a clickable, draggable HTML window to float over the DOM with a title
  ; bar and a close  button, imitating a native window. Make it sleek and
  ; beautiful, use HTML, CSS, and JavaScript.
  )
