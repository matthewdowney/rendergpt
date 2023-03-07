(ns com.mjdowney.rendergpt.build
  (:require [clojure.data.json :as json]
            [clojure.edn :as edn]))

;; Convert the manifest.edn file to JSON
(defn manifest
  {:shadow.build/stage :compile-prepare}
  [build-state & _]
  (spit "public/manifest.json"
    (with-out-str
      (-> (slurp "public/manifest.edn")
          edn/read-string
          (json/pprint))))
  (println (format "[%s] Wrote public/manifest.json" (:shadow.build/build-id build-state)))
  build-state)
