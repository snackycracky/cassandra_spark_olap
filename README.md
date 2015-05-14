# td_attic with Cassandra Database and Spark OLAP-Analysis

## Setting up Cassandra and creating the Facts Table

First installed and started cassandra:

```bash
#http://christopher-batey.blogspot.de/2013/05/installing-cassandra-on-mac-os-x.html
brew install cassandra
#run it with:
export JAVA_HOME=`/usr/libexec/java_home -v '1.8*'`
/usr/local/Cellar/cassandra/2.1.0/bin/cassandra
```

the actual cassandra shell can be reached with:

    cqlsh

the tested `fact` table looks like:

```sql
CREATE KEYSPACE td_attic
  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };

use td_attic;
drop table facts;
 large on one "partition row"
create table facts (
    fact_id UUID,
    type text,
    value float,
    dimensions set<text>, // with map<text:text> e.g. {'ccr':Brand, 'ccr':Market} would not be possible
    date timestamp,
    // the type and date compound key will be a reasonable partion compound key because it will not not grow data too
    PRIMARY KEY ((type, date), fact_id));

// this secondary index will not speed up cassandra!
CREATE INDEX fact_dimensions_idx ON facts(dimensions);

//duplicate data is possible
insert into facts (fact_id, type, value, dimensions, date) values (uuid(), 'youtube_visit', 14, {'ccr:Brand','FR:Market'}, '2015-01-01');
insert into facts (fact_id, type, value, dimensions, date) values (uuid(), 'youtube_visit', 13, {'ccl:Brand','FR:Market'}, '2015-01-01');
insert into facts (fact_id, type, value, dimensions, date) values (uuid(), 'youtube_visit', 15, {'ccz:Brand','FR:Market'}, '2015-01-02');
insert into facts (fact_id, type, value, dimensions, date) values (uuid(), 'youtube_visit', 15, {'ccz:Brand','FR:Market'}, '2015-01-02');
insert into facts (fact_id, type, value, dimensions, date) values (uuid(), 'youtube_visit', 15, {'ccz:Brand','FR:Market'}, '2015-01-03');

// the filtering by dimension with the cql statement `and dimensions contains 'ccr:Brand'` will not be efficient as cassandra replies with an error messagein the console -> using spark because of this.

SELECT value FROM facts WHERE
  type = 'youtube_visit' and
  date in ('2015-01-01', '2015-01-02');
```
 
## populated the database with 10.000.000 facts

created a new project called `data_generator` with the command:

    lein new data_generator

added `cassaforte` and `clj-time` to the project.clj:

```clojure
(defproject data_generator "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                [clojurewerkz/cassaforte "2.0.0"]
                [clj-time "0.9.0"]])
```
 
started clojure shell from the project root with the command `lein repl` and connected to the `td_attic` cassandra-keyspace.

```clojure
(ns cassaforte.docs
  (:require [clojurewerkz.cassaforte.client :as cc]
            [clojurewerkz.cassaforte.cql    :as cql]
            [clojurewerkz.cassaforte.query  :refer :all]
            [clojurewerkz.cassaforte.uuids  :as uuids]))

(def conn  (cc/connect ["127.0.0.1"]))

(cql/use-keyspace conn "td_attic")

(def markets ["DE" "FR" "ES" "CA" "RU"] )
(def brands ["CCR" "CCL" "Fanta" "CCZ" "Nestea"] )
(def types ["yt_visits" "webtrends_views" "ga_shares"] )

;; generate 10M random facts of these markets brands and types
(flatten ;;avoid large return in the console
    (for [x (range 0 10000000)
        :let [market (clojure.string/join [(rand-nth markets) ":Market"])]
        :let [brand (clojure.string/join [(rand-nth brands) ":Brand"])]]
        (cql/insert conn "facts"
            {:fact_id (uuids/random)
             :type (rand-nth types)
             :value (int (rand-int 30))
             :dimensions (set [market brand])
             :date (clojure.string/join ["2015-" (+ 1 (rand-int 11)) "-" (+ 1 (rand-int 28))]) })))


```

To count all facts in the keyspace a special query is needed according to the schema / partitions.
The approach was to iterate through all the 365 days in the year and over all types to get the counting of the keys (facts). The reason is that the date and the type together define a compound key on which cassandra is optimized.

```clojure
(ns td_attic.counting
  (:require [clojurewerkz.cassaforte.client :as cc]
            [clojurewerkz.cassaforte.cql    :as cql]
            [clojurewerkz.cassaforte.query  :refer :all]
            [clojurewerkz.cassaforte.uuids  :as uuids]
            [clj-time.core :as time]
            [clj-time.periodic :as time-period]
            [clj-time.format :as f]
            ))

(defn time-range
  "Return a lazy sequence of DateTime's from start to end, incremented by 'step' units of time."
  [start end step]
  (let [inf-range (time-period/periodic-seq start step)
        below-end? (fn [t] (time/within? (time/interval start end)
                                         t))]
    (take-while below-end? inf-range)))
 

(def conn (cc/connect ["127.0.0.1"]))

(cql/use-keyspace conn "td_attic")
 
(def types ["yt_visits" "webtrends_views" "ga_shares"] )
 
(def all_days_of_year (time-range (time/date-time 2015 01 01)
                                    (time/date-time 2016 01 01)
                                    (time/days 1)))
 
(count all_days_of_year) ;;will print 365
 
;;to count all the keys this code snippet queries td_attic for all records on all dates and all types and reduces the flattend results by summing them up.
(reduce + 
    (flatten
        (for [d all_days_of_year 
            :let [date (f/unparse (f/formatter "yyyy-MM-dd") d)]]
            (for [type types] 
                ;; now the actual cassandra cql count query is issued (will translate to select count(*) from facts where type = ... and date = ...)
                (cql/perform-count conn "facts" 
                    (where [[= :type type] [= :date date]]))
                ))))

```

## OLAP with Spark

checked out the http://spark.incubator.apache.org/screencasts/1-first-steps-with-spark.html and the following
I build spark from source there.
create a folder for all the development module:

```bash
cd ~/dev
mkdir td_cassandra_spark && cd td_cassandra_spark
```

Followed the steps from http://pulasthisupun.blogspot.de/2013/11/how-to-set-up-apache-spark-cluster-in.html which were:

```bash
wget http://d3kbcqa49mib13.cloudfront.net/spark-1.2.1.tgz
tar xzvf spark-1.2.1.tgz
cd spark-1.2.1
sbt/sbt compile && sbt/sbt assembly
```

added config to `conf/spark-env.sh`

```bash
export SPARK_WORKER_MEMORY=2g
export SPARK_WORKER_INSTANCES=2
export SPARK_WORKER_DIR=/Users/nils4tdd/dev/spark_worker_dir
```

added config to `conf/spark-defaults.sh`

```bash
# Default system properties included when running spark-submit.
# This is useful for setting default environmental settings.

# Example:
spark.master                     spark://127.0.0.1:7077
spark.driver.memory              2g
```

start the cluster

```bash
sbin/start-master.sh
sbin/start-slaves.sh
```

this can be checked on http://localhost:8080 if its working

```bash
wget https://github.com/datastax/spark-cassandra-connector/archive/v1.2.0-rc2.tar.gz
mv v1.2.0-rc2 v1.2.0-rc2.tar.gz
tar xzvf v1.2.0-rc2.tar.gz
cd spark-cassandra-connector-1.2.0-rc2
sbt/sbt compile && sbt/sbt assembly
```

now the shell can be started:

```bash
bin/spark-shell --jars ~/dev/td_cassandra_spark/spark-cassandra-connector-1.2.0-rc2/spark-cassandra-connector-java/target/scala-2.10/spark-cassandra-connector-java-assembly-1.2.0-rc2.jar
```

in the shell:

```scala
sc.stop
import com.datastax.spark.connector._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)
val rdd = sc.cassandraTable("td_attic", "facts")

//a query for filtering by the main properties of the td_attic schema
 
val yt_visit_on_beginning_of_year = rdd.
    select("dimensions", "value").
    where("type = ?", "yt_visits").
    where("date in ('2015-01-01', '2015-01-02')").
    toArray;

val summed_values = yt_visit_on_beginning_of_year.
    filter(row => row.get[List[String]]("dimensions").contains("CCZ:Brand")).
    map(row => row.get[Int]("value")).
    reduce((a, b) => a + b);

# will output the sum
#
# summed_values: Int = 30
```

to query a month for a timeline chart the shell had to be started with the joda time library (https://github.com/JodaOrg/joda-time/releases)

```bash
bin/spark-shell --jars ~/dev/td_cassandra_spark/spark-cassandra-connector-1.2.0-rc2/spark-cassandra-connector-java/target/scala-2.10/spark-cassandra-connector-java-assembly-1.2.0-rc2.jar,~/dev/td_cassandra_spark/lib/joda-time-2.7.jar
```

in the shell execute the code:

```scala
sc.stop
import com.datastax.spark.connector._
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.joda.time.Days
import org.joda.time.LocalDate

 
val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)
val rdd = sc.cassandraTable("td_attic", "facts")

 
val format = new java.text.SimpleDateFormat("dd-MM-yyyy")
val start_date = format.parse("01-03-2015")
val end_date = format.parse("01-04-2015")
 
val numberOfDays = Days.daysBetween(new LocalDate(start_date), new LocalDate(end_date)).getDays()

val dates = for (f<- 0 to numberOfDays) yield new LocalDate(start_date).plusDays(f)

val timeline_data = scala.collection.mutable.Map[String,String]()

// this is like a map function creating a new list out of a list:
for (date <- dates) yield { 

  //get the raw data
  val yt_visit_on_beginning_of_year = rdd.
    select("dimensions", "value").
    where("type = ?", "yt_visits").
    where(s"date = '$date'").
    toArray;
 
  //calculate on the data
  val sum = yt_visit_on_beginning_of_year.
    filter(row => row.get[List[String]]("dimensions").contains("CCZ:Brand")).
    map(row => row.get[Int]("value")).
    reduce((a, b) => a + b);

  timeline_data(date.toString()) = sum.toString()
}

//iterating over a map will require pattern match with `case 
timeline_data.foreach{case(date,value) => println (s"$date => $value")}

```

or a improved query which uses the spark group_by function:

```scala
val yt_visit_on_beginning_of_year = rdd.
    select("dimensions", "value", "date").
    where("type = ?", "yt_visits").
    where("date in ('2015-01-01', '2015-01-02', '2015-01-03', '2015-01-04', '2015-01-05', '2015-01-06', '2015-01-07', '2015-01-08', '2015-01-09', '2015-01-10')").
    toArray;

val filtered_rows = yt_visit_on_beginning_of_year.
    filter(row => row.get[List[String]]("dimensions").contains("CCZ:Brand"));
    
val grouped_by_date = filtered_rows.
    groupBy(row => {row.get[java.util.Date]("date")});    
    
val timeline_hash_as_list = grouped_by_date.
  map{case(date,rows) =>
    val value_sum = rows.map(row => row.get[Int]("value")).reduce((a, b) => a + b)
    List(date,value_sum)}
```


## cassandra performance improvement

changed the path to the commit-log in `/usr/local/etc/cassandra/cassandra.yaml` to a different HDD than the `data_file_directory`

```
...
  94 # Directories where Cassandra should store data on disk.  Cassandra
  95 # will spread data evenly across them, subject to the granularity of
  96 # the configured compaction strategy.
  97 # If not set, the default directory is $CASSANDRA_HOME/data/data.
  98 data_file_directories:
  99     - /Volumes/HDD2/cassandra/data
 100 
 101 # commit log.  when running on magnetic HDD, this should be a
 102 # separate spindle than the data directories.
 103 # If not set, the default directory is $CASSANDRA_HOME/data/commitlog.
 104 # commitlog_directory: /usr/local/var/lib/cassandra/commitlog
 105 commitlog_directory: /Users/nlis4tdd/dev/td_cassandra_spark/commitlogg
 106 
...
```
