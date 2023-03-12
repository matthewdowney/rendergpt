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

(defn add-to-code-block-title-bar! [code-block-ele new-ele]
  (if-let [existing-button (first (.getElementsByTagName code-block-ele "button"))]
    (.insertBefore (.-parentNode existing-button) new-ele existing-button)
    (.appendChild (.-parentElement code-block-ele) new-ele)))

;;; (2) A React component replacing the code block contents with an iframe for
;;; displaying the result of evaling / rendering the code.

(def all-code-blocks
  "All code blocks on the page, in order, with their types and source code."
  (r/atom [#_{:code "<div ...>" :type "html"}]))

(defn code-block-options [initial-selection-idx all-code-blocks]
  (into []
    (map-indexed
      (fn [idx {:keys [type]}]
        {:label (str type " @" (if (= idx initial-selection-idx) "here" idx))
         :value idx}))
    all-code-blocks))

(defn sources-dropdown
  "A multi-select dropdown for selecting which of ChatGPT's code sources to
  include, beyond the selected code block."
  [initial-selection-idx selected]
  (let [options (code-block-options initial-selection-idx @all-code-blocks)]
    [:> MultiSelect
     {:options (clj->js options)
      :overrideStrings #js {:selectSomeItems "Sources..."
                            :allItemsAreSelected "All sources selected"}
      :className "dark"
      :hasSelectAll true
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
  (let [selected (r/atom [code-block-idx])]
    (fn [code-block-idx]
      [:div.p4.overflow-y-auto.font-sans {:style {:min-height "500px"}}
       [sources-dropdown code-block-idx selected]
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
  (let [container (crate/html [:div {:style {:display "none"}}])]
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
          {:code (code-block-source ele) :type (code-block-type ele)}))

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
