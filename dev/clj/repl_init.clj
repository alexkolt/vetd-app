(ns repl-init
  (:require [com.vetd.app.core :as core]
            [figwheel-sidecar.repl-api :as fw]))

#_(do
    (import '(org.apache.log4j Logger Level))

    (.setLevel ( org.slf4j.LoggerFactory/getLogger org.slf4j.Logger/ROOT_LOGGER_NAME)
               ch.qos.logback.classic.Level/INFO))



(core/-main)
