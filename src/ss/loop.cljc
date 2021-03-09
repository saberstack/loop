(ns ss.loop
  (:require [clojure.core.async :as a]
            [taoensso.timbre :as timbre])
  #?(:cljs
     (:require-macros ss.loop)))


#?(:clj
   (defn random-uuid []
     (java.util.UUID/randomUUID)))


(defonce *id->stop-channels-set (atom {}))


(defn cleanup-state [id stop-ch]
  (swap! *id->stop-channels-set
    (fn [m]
      (let [s  (get m id)
            s' (disj s stop-ch)]
        (if (empty? s')
          ;empty set, remove :id from state
          (dissoc m id)
          ;just "update" the state with the new set
          (assoc m id s'))))))


(defn stop-loop [[id stop-channels-set]]
  (run!
    (fn [stop-ch] (a/put! stop-ch :stop))
    stop-channels-set))


(defn add-state [id stop-ch]
  (swap! *id->stop-channels-set
    (fn [m]
      (let [s  (get m id #{})
            s' (conj s stop-ch)]
        ;stop all existing loops with the same id
        (stop-loop [id s])
        ;return new state
        (assoc m id s')))))


(defn stop-all
  "Stops all loops. Returns true."
  []
  (run!
    stop-loop
    @*id->stop-channels-set)
  true)


(defn stop
  "Stop a loop by id. Returns true if the loop exists, nil otherwise"
  [id]
  (let [[_ _ :as e] (find @*id->stop-channels-set id)]
    (if e
      (do
        (stop-loop e)
        true)
      nil)))


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
  (let [?id-expr#  (or (get (meta bindings) :id)
                       (find-expr :id bindings))
        bindings'# (remove-ks #{:id} bindings)]

    `(let [stop-ch#  (a/promise-chan (filter (fn [x] (= x :stop))))
           id#       (random-uuid)
           id'# (if-let [?custom-id# ~?id-expr#]
                       ?custom-id#
                       id#)]
       (print-info [::start [:id id'#]])

       ;add stop-ch state
       (add-state id'# stop-ch#)
       ;start the loop
       (a/go
         (let [ret#
               (a/<! (a/go
                       ;the actual loop here
                       (loop ~bindings'#
                         (let [stop-or-nil# (a/poll! stop-ch#)]
                           (if (= stop-or-nil# :stop)
                             ;going to stop the loop
                             (do
                               (print-info [::stop [:id id'#]]))
                             ;else, continue
                             (do
                               ;user code
                               ~@body))))))]
           ;this has to be here in an outside loop in order to cleanup after user code finishes
           (cleanup-state id'# stop-ch#)
           ret#)))))

