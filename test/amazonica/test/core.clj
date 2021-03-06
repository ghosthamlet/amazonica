(ns amazonica.test.core
  (:import org.joda.time.DateTime
           java.text.SimpleDateFormat
           java.util.Date)
  (:require [clojure.string :as str])
  (:use [clojure.test]
        [clojure.pprint]
        [clojure.java.shell]
        [amazonica.core]
        [amazonica.aws.autoscaling      :exclude (delete-tags
                                                  describe-tags
                                                  get-service-abbreviation)]
        [amazonica.aws.elasticache      :exclude (describe-events )]
        [amazonica.aws.elasticbeanstalk :exclude (describe-events)]
        [amazonica.aws.rds              :exclude (describe-engine-default-parameters)]
        [amazonica.aws.redshift         :exclude (describe-events)]
        [amazonica.aws.simpledb         :exclude (create-domain
                                                  delete-domain)]
        [amazonica.aws.sns              :exclude (add-permission
                                                  remove-permission)]
        [amazonica.aws.storagegateway   :exclude (create-snapshot
                                                  delete-volume)]
        [amazonica.aws.glacier          :exclude (abort-multipart-upload
                                                  complete-multipart-upload
                                                  initiate-multipart-upload
                                                  list-multipart-uploads
                                                  list-parts)]
        [amazonica.aws.opsworks         :exclude (create-stack
                                                  delete-stack
                                                  describe-instances
                                                  describe-stacks
                                                  describe-volumes
                                                  update-stack)]
        [amazonica.aws
          cloudformation
          cloudfront
          cloudsearch
          cloudwatch
          datapipeline
          directconnect
          dynamodb
          ec2
          elasticloadbalancing
          elasticmapreduce
          identitymanagement
          route53
          s3
          simpleemail
          sqs]))

; config file contains space-separated AWS credential key pair
; and optional third param of AWS endpoint (e.g. for different
; region than the default US_East)
(def cred 
  (apply 
    hash-map 
      (interleave 
        [:access-key :secret-key :endpoint]
        (seq (.split (slurp "aws.config") " ")))))

(deftest s3 []
  
  (def bucket1 "amazonica8675309")
  (def bucket2 (str bucket1 "2"))
  (def date    (.plusDays (DateTime.) 2))
  (def upload-file   (java.io.File. "upload.txt"))
  (def download-file (java.io.File. "download.txt"))

  (.createNewFile upload-file)
  (spit upload-file (Date.))

  (get-s3account-owner cred)

  (let [b (:s3bucket (create-storage-location cred))
      _ (println "created bucket" b)]
  (delete-bucket cred b))

  (list-buckets cred)

  (create-bucket cred bucket1)
  (create-bucket cred bucket2 "us-west-1")

  (put-object cred
              :bucket-name bucket1
              :key "jenny"
              :file upload-file)


  (let [etag (put-object cred
                :bucket-name bucket1
                :key "jenny"
                :file upload-file)]
    (is 32 (.length (:etag etag))))

  
  (is "text/plain" 
    (get-in (get-object cred bucket1 "jenny")
            [:object-metadata :raw-metadata :Content-Type]))

  #_(get-object :bucket-name bucket1
                :key "jenny"
                download-file)
  

  (copy-object cred bucket1 "jenny" bucket2 "jenny")
  (delete-object cred bucket2 "jenny")

  (copy-object cred 
               :source-bucket-name bucket1
               :source-key "jenny" 
               :destination-bucket-name bucket2
               :destination-key "jenny")

  (generate-presigned-url cred bucket1 "jenny" date)
  (generate-presigned-url cred bucket2 "jenny" date)

  (delete-object cred bucket1 "jenny")
  (delete-object cred bucket2 "jenny")

  (delete-bucket cred bucket1)
  (delete-bucket cred bucket2)

  
  #_(clojure.pprint/pprint
    (list-objects cred bucket1))

  (.delete upload-file)
  (if (.exists download-file)
    (.delete download-file))

)


(deftest redshift []

  ; config file contains space-separated AWS credential key pair
  ; and optional third param of AWS endpoint (e.g. for different
  ; region than the default US_East)
  (apply defcredential 
    (seq (.split (slurp "aws.config") " ")))


  (println (describe-cluster-versions))
  (println (describe-clusters))


  (try
    (create-cluster-subnet-group :cluster-subnet-group-name "my subnet"
                                 :description "some desc"
                                 :subnet-ids ["1" "2" "3" "4"])
    (throw (Exception. "create-cluster-subnet-group did not throw exception"))
    (catch Exception e
      (is (.contains 
            (:message (ex->map e)) 
            "Some input subnets in :[1, 2, 3, 4] are invalid."))))
    
 (amazonica.aws.redshift/describe-events :source-type "Cluster")

  (try
    (modify-cluster-parameter-group :parameter-group-name "myparamgroup"
                                   :parameters [
                                    {:source          "user"
                                     :parameter-name  "my_new_param"
                                     :parameter-value "some value"
                                     :data-type       "String"
                                     :description     "some generic param"}

                                    {:source          "user"
                                     :parameter-name  "my_new_param-2"
                                     :parameter-value 42
                                     :data-type       "Number"
                                     :description     "some integer param"}])
    (throw (Exception. "modify-cluster-parameter-group did not throw exception"))
    (catch Exception e
      (is (.contains
            (:message (ex->map e))
            "Could not find parameter with name: my_new_param"))))

)


(deftest dynamodb []  

  (create-table cred 
                :table-name "TestTable"
                :key-schema {
                  :hash-key-element {
                    :attribute-name "id"
                    :attribute-type "S"
                  }
                }
                :provisioned-throughput {
                  :read-capacity-units 1
                  :write-capacity-units 1
                })

  (create-table cred
                :table-name "TestTable2"
                :key-schema {
                  :hash-key-element {
                    :attribute-name "id"
                    :attribute-type "S"
                  }
                  :range-key-element {
                    :attribute-name "range"
                    :attribute-type "S"
                  }
                }
                :provisioned-throughput {
                  :read-capacity-units 1
                  :write-capacity-units 1
                })

  (create-table cred
                :table-name "TestTable3"
                :key-schema {
                  :hash-key-element {
                    :attribute-name "id"
                    :attribute-type "S"
                  }
                }
                :provisioned-throughput [1 1])

  ; wait for the tables to be created
  (doseq [table (:table-names (list-tables cred))]
    (loop [status (get-in (describe-table cred :table-name table)
                          [:table :table-status])]
      (if-not (= "ACTIVE" status)
        (do 
          (println "waiting for status" status "to be active")
          (Thread/sleep 1000)
          (recur (get-in (describe-table cred :table-name table)
                          [:table :table-status]))))))
 

  (set-root-unwrapping! true)

  (is "id" (get-in 
            (describe-table cred :table-name "TestTable")
            [:key-schema :hash-key-element :attribute-name]))

  (set-root-unwrapping! false)

  (is "id" (get-in 
            (describe-table cred :table-name "TestTable")
            [:table :key-schema :hash-key-element :attribute-name]))
  
  (list-tables cred)
  (list-tables cred :limit 1)

  (dotimes [x 10] 
    (let [m {:id (str "1234" x) :text "joey t"}]
      (put-item cred 
                :table-name "TestTable" 
                :item m)))

  (put-item cred
            :table-name "TestTable"
            :item {
              :id "foo" 
              :text "barbaz"
            })

  (put-item cred
            :table-name "TestTable2"
            :item {
              :id { :s "foo" }
              :range { :s "foo" } 
              :text { :s "zonica" }
            })
  (try
    (get-item cred
              :table-name "TestTableXXX"
              :key "foo")
  (catch Exception e
    (let [error-map (ex->map e)]
      (is (contains? error-map :error-code))
      (is (contains? error-map :status-code))
      (is (contains? error-map :service-name))
      (is (contains? error-map :message)))))

  (query cred
         :table-name "TestTable2"
         :limit 1
         :hash-key-value "mofo"
         :range-key-condition {
           :attribute-value-list ["f"]
           :comparison-operator "BEGINS_WITH"
          })

  (clojure.pprint/pprint
    (scan cred :table-name "TestTable"))

  (set-root-unwrapping! false)

  (clojure.pprint/pprint (batch-get-item cred :request-items {
     "TestTable" { :keys [
                     {:hash-key-element {:s "foo"}}
                     {:hash-key-element {:s "1234"}}
                   ]
                   :consistent-read true
                   :attributes-to-get ["id" "text"]}}))

  
  #(try
    (batch-write-item cred :request-items {
    "TestTable" [
      {:put-request {
        :item {
          :id "1234"
          :text "mofo"}}}
      {:put-request {
        :item {
          :id "foo"
          :text "barbarbanks"}}}]})
    (catch Exception e
      (println (.printStackTrace e))))

  (clojure.pprint/pprint 
    (describe-table cred :table-name "TestTable"))

  (delete-table cred :table-name "TestTable")
  (delete-table cred :table-name "TestTable2")
  (delete-table cred :table-name "TestTable3")

)


(deftest ec2 []

  ; config file contains space-separated AWS credential key pair
  ; and optional third param of AWS endpoint (e.g. for different
  ; region than the default US_East)
  (apply defcredential 
    (seq (.split (slurp "aws.config") " ")))

  (clojure.pprint/pprint
    (list-available-solution-stacks))

  (clojure.pprint/pprint
    (describe-availability-zones))

  (clojure.pprint/pprint
    (describe-dhcp-options))

  #_(clojure.pprint/pprint
    (describe-images :owners ["self"]))

  (clojure.pprint/pprint
    (describe-instances))

  (let [image-id (create-image 
                  :name "my_test_image"
                  :instance-id "i-1b9a9f71"
                  :description "test image - safe to delete"
                  :block-device-mappings [
                    {:device-name  "/dev/sda1"
                     :virtual-name "myvirtual"
                     :ebs {
                       :volume-size 8
                       :volume-type "standard"
                       :delete-on-termination true}}])]
    (deregister-image :image-id (:image-id image-id)))
    ;(deregister-image :image-id "ami-f00f9699")
)


(deftest cloudwatch []

  (clojure.pprint/pprint
    (list-metrics :metric-name "ThrottledRequests"
                  :namespace "AWS/DynamoDB"))

  (set-date-format! "MM-dd-yyyy")

  (clojure.pprint/pprint
    (let [date-string (.. (SimpleDateFormat. "MM-dd-yyyy")
                          (format (Date.)))]
      (get-metric-statistics :metric-name "ThrottledRequests"
                             :namespace "AWS/DynamoDB"
                             :start-time (.minusDays (DateTime.) 1)
                             :end-time date-string
                             :period 60
                             :statistics ["Sum" "Maximum" "Minimum" 
                                          "SampleCount" "Average"])))

  (clojure.pprint/pprint
    (describe-alarms :max-records 100))

  (clojure.pprint/pprint
    (describe-alarm-history :max-records 100))
)