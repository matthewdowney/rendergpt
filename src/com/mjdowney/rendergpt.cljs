(ns com.mjdowney.rendergpt
  (:require [crate.core :as crate]
            [reagent.dom :as rdom]
            [reagent.core :as r]
            ["react-multi-select-component" :refer (MultiSelect)]))

(defn build-render-button []
  (crate/html [:button.flex.ml-auto.gap-2 "Render"]))

(defn render-dropdown []
  (let [selected (r/atom [])]
    (fn []
      [:> MultiSelect
       {:options (clj->js
                   [{:label "Option 1" :value "option1"}
                    {:label "Option 2" :value "option2"}
                    {:label "Option 3" :value "option3"}])
        :className "dark"
        :hasSelectAll false
        :disableSearch true
        :onChange (fn [e] (js/console.log "Selected:" (reset! selected (js->clj e))))
        :value @selected}])))

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

(defn iframe [srcdoc]
  (crate/html
    [:div.p4.overflow-y-auto {:style {:min-height "500px" :display "none"}}
     [:iframe
      {:srcdoc srcdoc
       :style {:position "relative"
               :width "100%"
               :min-height "500px"
               :border "1px solid steelblue"
               :border-radius "5px"
               :background-color "white"}}]]))

(defn create-rendered-iframe [ele]
  (let [src-code (code-block-source ele)
        f (iframe (code-block-source ele))]
    (js/console.log "Code:" src-code)
    (.appendChild ele f)
    f))

(defn toggle-render-fn [ele render-button]
  (let [rendered? (atom false)
        hide! (fn [e] (set! (-> e .-style .-display) "none"))
        show! (fn [e] (set! (-> e .-style .-display) "block"))]
    (fn [_e]
      (let [children (.-children ele)
            content (aget children 1)
            rendered-iframe (if (>= (.-length children) 3)
                              (aget children 2)
                              (create-rendered-iframe ele))]
        (if @rendered?
          (do
            (hide! rendered-iframe)
            (show! content)
            (set! (.-innerText render-button) "Render"))
          (do
            (hide! content)
            (show! rendered-iframe)
            (set! (.-innerText render-button) "Source")))

        (reset! rendered? (not @rendered?))))))

(defn on-mutation [mutation-records _observer]
  (js/console.log "Mutation observed" mutation-records)
  (doseq [ele (get-gpt-response-code-blocks)]
    (when (= (code-block-type ele) "html")
      (set-code-block-type! ele "html+")

      (let [render-button (build-render-button)
            toggle-render (toggle-render-fn ele render-button)]
        (.addEventListener render-button "click" toggle-render)
        (add-to-code-block-title-bar! ele render-button))

      (let [render-dropdown-div (crate/html [:div.rendergpt-dropdown])
            #_#_render-dropdown (render-dropdown)
            #_#_select-ele (second (.-children render-dropdown))]
        #_(.addEventListener (first (.-children render-dropdown)) "click"
          (fn [_e]
            (set! (.-display (.-style select-ele))
              (if (= (.-display (.-style select-ele)) "none")
                "block"
                "none"))))
        (add-to-code-block-title-bar! ele render-dropdown-div)
        (rdom/render [render-dropdown] render-dropdown-div)))))

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
