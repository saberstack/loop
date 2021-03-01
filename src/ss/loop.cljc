(ns ss.loop
  (:require [clojure.core.async :as a]
            [taoensso.timbre :as timbre])
  #?(:cljs
     (:require-macros ss.loop)))


#?(:clj
   (defn random-uuid []
     (java.util.UUID/randomUUID)))


(defonce *id->stop-ch (atom {}))


(defn cleanup [id stop-ch]
  (swap! *id->stop-ch
    (fn [m]
      (let [-stop-ch (get m id)]
        ;IMPORTANT!
        ;make sure we don't cleanup another loop's stop-ch
        ;check if channels are the same
        (if (= -stop-ch stop-ch)
          ;yes, cleanup
          (dissoc m id)
          ;else, return as-is
          m)))))


(defn stop-loop [[id stop-ch]]
  (a/put! stop-ch :stop))


(defn stop-all
  "Stops all loops. Returns true."
  []
  (run!
    stop-loop
    @*id->stop-ch)
  true)


(defn stop
  "Stop a loop by id. Returns true if the loop exists, nil otherwise"
  [id]
  (let [[_ _ :as e] (find @*id->stop-ch id)]
    (if e
      (do
        (stop-loop e)
        true)
      nil)))


(defn start-go
  "start-go-fn - fn that starts a go, takes new-uuid and stop-ch; needs to take care of stopping the go via alts!"
  [start-go-fn]
  (let [stop-ch (a/promise-chan (filter (fn [x] (= x :stop))))
        id      (random-uuid)]
    (start-go-fn id stop-ch)))


(defn find-expr [k bindings]
  (let [[_ expr] (->> bindings
                      (partition 2)
                      (map vec)
                      (filter (fn [[-k v]] (= -k k)))
                      (first))]
    expr))


(defn remove-ks [ks-set bindinds]
  (->> bindinds
       (partition 2)
       (map vec)
       (remove (fn [[k v]] (contains? ks-set k)))
       (flatten)
       (vec)))


(defn print-info [v]
  (timbre/info v))


(defmacro go-loop [bindings & body]
  (let [?id-expr#  (find-expr :id bindings)
        bindings'# (remove-ks #{:id} bindings)]
    `(start-go
       (fn [id# stop-ch#]
         (let [final-id# (if-let [?custom-id# ~?id-expr#]
                           ?custom-id#
                           id#)]
           (print-info [::start [:id final-id#]])
           ;stop loop with the same :id (if it exists)
           (stop final-id#)
           ;add stop-ch to atom
           (swap! *id->stop-ch (fn [m#] (assoc m# final-id# stop-ch#)))
           ;start the loop
           (a/go
             (let [ret# (a/<! (a/go
                                ;the actual loop here
                                (loop ~bindings'#
                                  (let [stop-or-nil# (a/poll! stop-ch#)]
                                    (if (= stop-or-nil# :stop)
                                      ;going to stop the loop
                                      (do
                                        (print-info [::stop [:id final-id#]]))
                                      ;else, continue
                                      (do
                                        ;user code
                                        ~@body))))))]
               (cleanup final-id# stop-ch#)
               ret#)))))))

