(ns plasma.query-test
  (:use clojure.test
        plasma.query))

(deftest path-start []
  (is (= true true)))

(def q (path [:foo :bar]))


