(defproject com.brunobonacci/ip-geoloc "0.1.0-SNAPSHOT"
  :description "A Clojure library for IP GeoLocation"
  :url "https://github.com/BrunoBonacci/ip-geoloc"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.maxmind.geoip2/geoip2 "2.3.1"]
                 [clj-http "1.1.2"]
                 [pandect "0.5.2"]
                 [com.stuartsierra/component "0.2.3"]]

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo/"
                                    :sign-releases false}]])
