(ns com.mjdowney.rendergpt
  (:require [crate.core :as crate]
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

(defn sources-dropdown
  "A multi-select dropdown for selecting which of ChatGPT's code sources to
  include, beyond the selected code block."
  []
  (let [selected (r/atom [])]
    (fn []
      [:> MultiSelect
       {:options (clj->js
                   [{:label "Option 1" :value "option1"}
                    {:label "Option 2" :value "option2"}
                    {:label "Option 3" :value "option3"}])
        :overrideStrings #js {:selectSomeItems "Sources..."
                              :allItemsAreSelected "All sources selected"}
        :className "dark"
        :hasSelectAll false
        :disableSearch true
        :onChange (fn [e] (js/console.log "Selected:" (reset! selected (js->clj e))))
        :value @selected}])))

(defn rendergpt [srcdoc]
  [:div.p4.overflow-y-auto.font-sans {:style {:min-height "500px"}}
   [sources-dropdown]
   [:iframe
    {:srcDoc srcdoc
     :style {:position "relative"
             :width "100%"
             :min-height "500px"
             :border "1px solid steelblue"
             :border-radius "5px"
             :background-color "white"}}]])

;;; (3) Vanilla JS to add a button element to each code block which injects the
;;; above react component.

(defn create-rendergpt-ele [parent]
  (let [src-code (code-block-source parent)
        container (crate/html [:div {:style {:display "none"}}])]
    (js/console.log "Code:" src-code)
    (.appendChild parent container)
    (rdom/render [rendergpt src-code] container)
    container))

(defn toggle-render-fn [ele render-button]
  (let [rendered? (atom false)
        hide! (fn [e] (set! (-> e .-style .-display) "none"))
        show! (fn [e] (set! (-> e .-style .-display) "block"))]
    (fn [_e]
      (let [children (.-children ele)
            content (aget children 1)
            rgpt (if (>= (.-length children) 3)
                   (aget children 2)
                   (create-rendergpt-ele ele))]
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
  (doseq [ele (get-gpt-response-code-blocks)]
    (when (= (code-block-type ele) "html")
      (set-code-block-type! ele "html+")

      (let [render-button (crate/html [:button.flex.ml-auto.gap-2 "Render"])
            toggle-render (toggle-render-fn ele render-button)]
        (.addEventListener render-button "click" toggle-render)
        (add-to-code-block-title-bar! ele render-button)))))

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
