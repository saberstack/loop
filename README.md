# Loop: Take control of your core.async loops! 

    ;deps.edn
    saberstack/loop {:git/url "https://github.com/saberstack/loop" :sha "1e8181facea003375f46af3ff68543e451319b35"}


## Usage

Add require:

    (ns my-ns 
      (:require 
        [ss.loop :as ss|a]
        [clojure.core.async :as a]))

Start a core.async go-loop as usual, but using the ss.loop/go-loop macro:

    (ss|a/go-loop [i 0]
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

Now, stop the go-loop from the REPL:

    ;stops all running go-loops started via ss.loop/go-loop
    (ss|a/stop-all)

    => true
    ;... INFO [saberstack.loop:66] - [:saberstack.loop/stop [:id #uuid "8a4a2d21-1a9d-4a71-b017-b97169517db6"]]


ss.loop/go-loop also supports giving identifier to a loop: 

    (ss|a/go-loop [i 0
                   :id 42]
      (println i)
      (<! (timeout 500))
      (recur (inc i)))

Now you can stop only this loop:

    (ss|a/stop 42)

    ;returns true if the loop exists, nil otherwise
    ;=> true
    ;... INFO [saberstack.loop:66] - [:saberstack.loop/stop [:id 42]]


## License

Copyright © 2021 raspasov

Distributed under the Eclipse Public License 1.0
