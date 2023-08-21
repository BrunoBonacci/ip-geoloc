(defproject com.brunobonacci/ip-geoloc "0.3.0-SNAPSHOT"
  :description "A fully automated Clojure library for IP GeoLocation"
  :url "https://github.com/BrunoBonacci/ip-geoloc"

  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}

  :scm {:name "git" :url "https://github.com/BrunoBonacci/ip-geoloc"}

  :dependencies [[org.clojure/clojure "1.11.1"]
                 [com.maxmind.geoip2/geoip2 "4.1.0"]
                 [clj-http "3.12.3"]
                 [pandect "1.0.2"]
                 [com.brunobonacci/safely "0.7.0-alpha3"]
                 [org.clojure/tools.logging "1.2.4"]])
