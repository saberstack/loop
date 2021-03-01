# Loop: Take control of your core.async loops!

```clojure
;deps.edn
saberstack/loop {:git/url "https://github.com/saberstack/loop"
                 :sha     "456f1e20f626d9c982c9a7f22eb8853c167c1ef8"}
```
## Usage

Add require:

```clojure
(ns my-ns
  (:require
    [ss.loop :as ss|a]
    [clojure.core.async :as a]))
```

Start a core.async go-loop as usual, but using the ss.loop/go-loop macro:

```clojure
(ss|a/go-loop
  [i 0]
  (println i)
  (<! (timeout 500))
  (recur (inc i)))

;starts printing
;=>
;0
;1
;2
;3
;...
```

Now, stop the go-loop from the REPL:

```clojure
;stops all running go-loops started via ss.loop/go-loop
(ss|a/stop-all)

;=> true
;... INFO [saberstack.loop:66] - [:saberstack.loop/stop [:id #uuid "..."]]
```

ss.loop/go-loop also supports giving identifier to a loop:

```clojure
(ss|a/go-loop
  [i 0
   :id 42]
  (println i)
  (<! (timeout 500))
  (recur (inc i)))
```

Now you can stop only this loop:

```clojure
(ss|a/stop 42)

;returns true if the loop exists, nil otherwise
;=> true
;... INFO [saberstack.loop:66] - [:saberstack.loop/stop [:id 42]]
```
If you start a second go-loop with the same :id, the first loop will be send a stop call.

A ss.loop/go-loop always exits on the very next (recur ...) call. It does not "die" automagically in the middle of execution.

IMPORTANT: if the first loop is stuck waiting in its own code, say via (<! ...), there's no guarantee that it will be stopped before the second loop begins. 


## License

Copyright © 2021 raspasov

Distributed under the Eclipse Public License 1.0
