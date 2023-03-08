(ns com.mjdowney.rendergpt
  (:require [crate.core :as crate]))

(defn inject-stylesheet [href]
  (let [link (js/document.createElement "link")]
    (set! (.-rel link) "stylesheet")
    (set! (.-type link) "text/css")
    (set! (.-href link) href)
    (.appendChild (.-head js/document) link)))

(defn build-render-button []
  (crate/html [:button.flex.ml-auto.gap-2 "Render"]))

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
  (let [width (/ (.-clientWidth (.-body js/document)) 2)
        height (/ (.-clientHeight (.-body js/document)) 2)]
    (crate/html
      [:iframe
       {:srcdoc srcdoc
        :style {:position "fixed"
                :top "50%"
                :left "50%"
                :width (str width "px")
                :height (str height "px")
                :transform "translate(-50%, -50%)"
                :border "1px solid steelblue"
                :border-radius "5px"
                :background-color "white"}}])))


(defn on-mutation [mutation-records _observer]
  (js/console.log "Mutation observed" mutation-records)
  (doseq [ele (get-gpt-response-code-blocks)]
    (when (= (code-block-type ele) "html")
      (set-code-block-type! ele "html+")

      (let [render-button (build-render-button)]
        (.addEventListener render-button "click"
          (fn [_e]
            (js/console.log "Code:" (code-block-source ele))
            (.appendChild js/document.body (iframe (code-block-source ele)))))

        (add-to-code-block-title-bar! ele render-button)))))

(defn register-on-mutation [f]
  (let [observer (js/MutationObserver. f)]
    (.observe observer
      (.-body js/document)
      #js {:childList true :subtree true})))

(defn init []
  (inject-stylesheet (js/chrome.runtime.getURL "rendergpt.css"))
  (register-on-mutation on-mutation)
  (js/console.log "injected rendergpt"))
