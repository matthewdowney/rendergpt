(ns com.mjdowney.rendergpt
  (:require [clojure.string :as string]
            [crate.core :as crate]
            [reagent.dom :as rdom]
            [reagent.core :as r]
            ["react-multi-select-component" :refer (MultiSelect)]))

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
  (let [chat-parent-sel "flex flex-col items-center text-sm dark:bg-gray-800"
        chat-parent (first (js/document.getElementsByClassName chat-parent-sel))
        user-msgs (reverse (take-nth 2 (.-children chat-parent)))]
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
        [:span.code-selection-desc (:desc option)]]])))

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

(defn code-block-html [{:keys [code type]}]
  (case type
    "html" code
    "javascript" (str "<script>\n" code "\n</script>")
    "css" (str "<style>\n" code "\n</style>")

    ; if the code type is unknown, just embed it directly
    code))

(defn rendergpt [code-block-idx]
  (let [selected (r/atom [code-block-idx])
        hover? (r/atom false)]
    (fn [code-block-idx]
      [:div.p4.overflow-y-auto.font-sans {:style {:min-height "500px"}}
       [:div {:onMouseOver (fn [_e] (reset! hover? true))
              :onMouseOut (fn [_e] (reset! hover? false))}
        [:span.tooltip {:style {:display (if @hover? "block" "none")}}
         "Select which code blocks from ChatGPT's responses to render."]
        [sources-dropdown code-block-idx selected]]
       ;; TODO: Perhaps this needs to be a type 3 component, and re-initialize
       ;;       completely when sources change.
       [:iframe
        {:srcDoc (->> @selected
                      (map (fn [idx] (code-block-html (nth @all-code-blocks idx))))
                      (string/join "\n"))
         :style {:position         "relative"
                 :width            "100%"
                 :min-height       "500px"
                 :border           "1px solid steelblue"
                 :border-radius    "5px"
                 :background-color "white"}}]])))

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

(defn on-mutation [mutation-records _observer]
  (js/console.log "Mutation observed" mutation-records)
  (let [n-registered (count @all-code-blocks)]
    (doseq [[idx ele] (map-indexed vector (get-gpt-response-code-blocks))]

      (when (>= idx n-registered)
        (swap! all-code-blocks conj
          {:code (code-block-source ele)
           :type (code-block-type ele)
           :desc (prompt-for-code-block ele)}))

      (when (= (code-block-type ele) "html")
        (set-code-block-type! ele "html+")

        (let [render-button (crate/html [:button.flex.ml-auto.gap-2 "Render"])
              toggle-render (toggle-render-fn ele idx render-button)]
          (.addEventListener render-button "click" toggle-render)
          (add-to-code-block-title-bar! ele render-button))))))

(defn register-on-mutation [f]
  (let [observer (js/MutationObserver. f)]
    (.observe observer
      (.-body js/document)
      #js {:childList true :subtree true})))

(defn inject-stylesheet [href]
  (.appendChild js/document.head
    (crate/html
      [:link {:rel "stylesheet" :type "text/css" :href href}])))

(defn init []
  (inject-stylesheet (js/chrome.runtime.getURL "rendergpt.css"))
  (register-on-mutation on-mutation)
  (js/console.log "injected rendergpt"))
