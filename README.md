# Loop: Take control of your core.async go-loops!

[![Clojars Project](https://img.shields.io/clojars/v/saberstack/loop.svg)](https://clojars.org/saberstack/loop)

## Install

```clojure
;deps.edn
saberstack/loop {:mvn/version "0.2.3"}

;Leiningen/Boot
[saberstack/loop "0.2.3"]
```

## Usage

### Works the same in Clojure and ClojureScript

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
  (a/<! (a/timeout 500))
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

ss.loop/go-loop also supports giving each go-loop an identifier via metadata:

```clojure
(ss|a/go-loop
  ^{:id 42}
  [i 0]
  (println i)
  (a/<! (a/timeout 500))
  (recur (inc i)))
```

Now you can stop only this loop:

```clojure
(ss|a/stop 42)

;returns true if the loop exists, nil otherwise
;=> true
;... INFO [saberstack.loop:66] - [:saberstack.loop/stop [:id 42]]
```

If you start a second go-loop with the same :id, the first loop will be sent a stop call.

A ss.loop/go-loop always exits on the very next (recur ...) call. It does not "die" automagically in the middle of
execution.

IMPORTANT: if the first loop is stuck waiting in its own code, say via (<! ...), there's no guarantee that it will be
stopped before the second loop begins.

## License

Copyright © 2021 raspasov

Distributed under the Eclipse Public License 1.0
