(ns org.saberstack.clojure.core.async.loop
  (:require [clojure.core.async :as a]
            [taoensso.trove :as trove])
  #?(:cljs
     (:require-macros org.saberstack.clojure.core.async.loop)))

#?(:clj
   (defn new-random-uuid []
     (java.util.UUID/randomUUID))
   :cljs
   (defn new-random-uuid []
     (cljs.core/random-uuid)))

;map of :an-id to a-set like {:some-id #{}}
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

(defmacro thread-loop [bindings & body]
  (let [?id-expr# (get (meta bindings) :id)]
    `(let [stop-ch# (a/promise-chan (filter (fn [x#] (= x# :stop))))
           id#      (new-random-uuid)
           id'#     (if-let [?custom-id# ~?id-expr#]
                      ?custom-id#
                      id#)]
       (trove/log! {:id ::starting :data {:id id'#}})

       ;add stop-ch state
       (add-state id'# stop-ch#)
       ;start the loop
       (a/thread
         (let [ret#
               (a/<!! (a/thread
                        ;the actual loop here
                        (loop ~bindings
                          (let [stop-or-nil# (a/poll! stop-ch#)]
                            (if (= stop-or-nil# :stop)
                              ;going to stop the loop
                              (trove/log! {:id ::stopped :data {:id id'#}})
                              ;else, continue
                              (do
                                ;user code
                                ~@body))))))]
           ;this has to be here in an outside loop in order to cleanup after user code finishes
           (cleanup-state id'# stop-ch#)
           ret#)))))

(defmacro go-loop [bindings & body]
  (let [?id-expr# (get (meta bindings) :id)]
    `(let [stop-ch# (a/promise-chan (filter (fn [x#] (= x# :stop))))
           id#      (new-random-uuid)
           id'#     (if-let [?custom-id# ~?id-expr#]
                      ?custom-id#
                      id#)]
       (trove/log! {:id ::starting :data {:id id'#}})

       ;add stop-ch state
       (add-state id'# stop-ch#)
       ;start the loop
       (a/go
         (let [ret#
               (a/<! (a/go
                       ;the actual loop here
                       (loop ~bindings
                         (let [stop-or-nil# (a/poll! stop-ch#)]
                           (if (= stop-or-nil# :stop)
                             ;going to stop the loop
                             (trove/log! {:id ::stopped :data {:id id'#}})
                             ;else, continue
                             (do
                               ;user code
                               ~@body))))))]
           ;this has to be here in an outside loop in order to cleanup after user code finishes
           (cleanup-state id'# stop-ch#)
           ret#)))))
