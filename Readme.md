## Joins in MapReduce

This project illustrates the usage of mapreduce joins

It could be very useful to perform Joins in Hadoop to get more insights from your data. MapReduce could be
cumbersome in writing the joins from scratch. You can use high level frameworks like hive and pig to do the 
same operations very effectively. It would be very helpful if you know the details of how mapreduce implement
joins.

### Overview:

* Requirements
  * Getting the data into HDFS required for examples
  * Create a [Map file](http://hadoop.apache.org/docs/current/api/org/apache/hadoop/io/MapFile.html) for departments 
    data
* MapSide Joins
  * Using Distributed Cache
  * Using Composite File Input Format
* ReduceSide Joins
  * Regular way
  * Using Join Package
  
### Getting the data into HDFS

* Download the mysql employee dataset from [here](https://dev.mysql.com/doc/employee/en/employees-installation.html)
* Once downloaded, load the dataset into MySql

```
wget https://launchpad.net/test-db/employees-db-1/1.0.6/+download/employees_db-full-1.0.6.tar.bz2
tar xjf employees_db-full-1.0.6.tar.bz2
cd employees_db && mysql -t < employees.sql
```

* Use [sqoop](http://sqoop.apache.org/) to get data in a required format into HDFS

Sqoop in the Employees table:

```
sqoop import --connect jdbc:mysql://sandbox.hortonworks.com/employees --username root \
  --query 'SELECT e.emp_no, e.birth_date, e.first_name, e.last_name, e.gender, e.hire_date, d.dept_no FROM employees e, dept_emp d WHERE (e.emp_no = d.emp_no) AND $CONDITIONS' \
  --target-dir /user/root/employees --split-by e.emp_no
```

Final dataset format:

```
[EmpNo, DOB, FName, LName, Gender, HireData, DeptID]
```

Sqoop in Salaries History table:

```
sqoop import --connect jdbc:mysql://sandbox.hortonworks.com/employees \
  --username root --table salaries --target-dir /user/root/salaryhistory
```

Final dataset format:

```
[EmpNo, salary, FromDate, ToDate]
```

Sqoop in Salaries table:

```
sqoop import --connect jdbc:mysql://sandbox.hortonworks.com/employees --username root \
  --query 'SELECT emp_no, salary, from_date, to_date FROM salaries WHERE (to_date = "9999-01-01") AND $CONDITIONS' \
  --target-dir /user/root/salaries --split-by emp_no 
```

Final dataset format:

```
[EmpNo, salary, FromDate, ToDate]
```

Sqoop in Departments table:

```
sqoop import --connect jdbc:mysql://sandbox.hortonworks.com/employees --username root \
  --query 'SELECT dept_no, dept_name FROM departments ORDER BY dept_no AND $CONDITIONS' \
  --target-dir /user/root/departments --split-by dept_no -m1
```

Final dataset format:

```
[DeptNo, DeptName]
```


#### Create a map file

This departments map file will be distributed to all nodes using 
[Distributed Cache](https://hadoop.apache.org/docs/r2.2.0/api/org/apache/hadoop/filecache/DistributedCache.html), which 
will be used in map side and reduce side joins.
 
Compile the code base:

```
mvn package
```

Run the converter:

```
hadoop jar target/joins-1.0-SNAPSHOT.jar \
  com.cloudwick.mapreduce.joins.utils.CreateDepartmentsMapFile \
  departments/part-m-00000 \
  depts.map
```

### ReduceSide (Repartition) Join

Reduce side join takes advantage of MapReduce's sort & merge to group the records together, it can be implemented as a
single MapReduce job, and can support N-way join, where N is the number of datasets being joined.

Tha Map phase is responsible for reading the data from various datasets, determine the join key and tag the record to
identify which dataset the record belongs to.

A single reducer invocation receives all of the values for a join key emitted by the map function and partitions the 
data into N partitions. After the reducer has read all of the input records for the join value and partitioned them
in memory, it performs a Cartesian product across all partitions and emits the results of each join.

#### The Regular Way

What's Involved:

* The key of the map output, of datasets being joined, has to be the join key - so they reach the same reducer
* Each dataset has to be tagged with its identity, in the mapper - to help differentiate between the datasets in the 
  reducer, so they can be processed accordingly.
* In each reducer, the data values from both datasets, for keys assigned to the reducer, are available, to be processed 
as required.
* A secondary sort needs to be done to ensure the ordering of the values sent to the reducer
* If the input files are of different formats, we would need separate mappers, and we would need to use 
  [MultipleInputs](https://hadoop.apache.org/docs/stable/api/org/apache/hadoop/mapreduce/lib/input/MultipleInputs.html) 
  class in the driver to add the inputs and associate the specific mapper to the same.
  
**TODO**: add an image illustrating both cardinality 1..1 and 1..many

Implementing it:

**Components:** (in *com.cloudwick.mapreduce.joins.reduceside.regular*)
                 
1. Map output key - The key will be the EmpNo as it is the join key for the datasets employee and salary.
  [Implementation in *MapperRSJ*]
2. Tagging the data with the dataset identity - Add an attribute called `srcIndex` to tag the identity of the data
  (1=employee, 2=salary, 3=salary_history). [Implementation in *MapperRSJ*]
3. Discarding unwanted attributes [Implementation in *MapperRSJ*]
4. Composite Key - Make the map output key a composite of EmpNo and SrcIndex [Implementation *CompositeKeyWritableRSJ*]
5. Partitioner - Partition the data on natural key of EmpNo [Implementation *PartitionerRSJ*]
6. Sorting - Sort the data on EmpNo first, and then source index [Implementation create *SortingComparatorRSJ*]
7. Grouping - Group the data based on natural key [Implementation - create custom *GroupingComparatorRSJ*]
8. Joining - Iterate through the values for a key and composite the join for employee and salary data, preform lookup
  of department to include department name in the output. [Implementation: *ReducerRSJ*]


Running the job:

```
hadoop jar target/joins-1.0-SNAPSHOT.jar \
  com.cloudwick.mapreduce.joins.reduceside.Driver \
  employees \
  salaryhistory \
  depts.map \
  outputrsj
```

### MapSide Joins

To take advantage of map-side joins our data must meet one of following criteria:

1. The datasets to be joined are already sorted by the same key and have the same number of partitions
2. Of the two datasets to be joined, one is small enough to fit into memory


### MapSide (Replication) Join Using DC

The mapper program retrieves the department data available through distributed cache and and loads the same into a 
HashMap in the setUp() method of the mapper, and the HashMap is referenced in the map method to get the department name, 
and emit the employee dataset with department name included.


#### Using Map File

```
hadoop jar target/joins-1.0-SNAPSHOT.jar \
  com.cloudwick.mapreduce.joins.mapside.dc.mapfile.Driver \
  employees \
  depts.map \
  outputmsm
```

### Using Text File

```
hadoop jar target/joins-1.0-SNAPSHOT.jar \
  com.cloudwick.mapreduce.joins.mapside.dc.textfile.Driver \
  employees \
  departments/part-m-00000 \
  outputmst
```


### MapSide Join on Large Files

Implementation of map-side join of large datasets using CompositeInputFormat.

To be able to perform map-side joins we need to have our data sorted by the same key and have the same number of 
partitions, implying that all keys for any record are in the same partition. While this seems to be a tough 
requirement, it is easily fixed. Hadoop sorts all keys and guarantees that keys with the same value are sent to the 
same reducer. So by simply running a MapReduce job that does nothing more than output the data by the key you want to 
join on and specifying the exact same number of reducers for all datasets, we will get our data in the correct form. 
Considering the gains in efficiency from being able to do a map-side join, it may be worth the cost of running 
additional MapReduce jobs. It bears repeating at this point it is crucial all datasets specify the exact same number 
of reducers during the “preparation” phase when the data will be sorted and partitioned. In this post we will take 
two data-sets and run an initial MapReduce job on both to do the sorting and partitioning and then run a final job 
to perform the map-side join. First let’s cover the MapReduce job to sort and partition our data in the same way.
                                      
When performing a map-side join the records are merged before they reach the mapper. To achieve this, we use the 
CompositeInputFormat. We will also need to set some configuration properties. 

First, we are specifying the character that separates the key and values by setting the 
`mapreduce.input.keyvaluelinerecordreader.key.value.separator` property. Next we use the `CompositeInputFormat.compose` 
method to create a “join expression” specifying an inner join by using the word “inner”, then specifying the input 
format to use, the KeyValueTextInputclass and finally a String varargs representing the paths of the files to join 
(which are the output paths of the map-reduce jobs ran to sort and partition the data). The `KeyValueTextInputFormat` 
class will use the separator character to set the first value as the key and the rest will be used for the value.


```
hadoop jar target/joins-1.0-SNAPSHOT.jar \
  com.cloudwick.mapreduce.joins.mapside.largefiles.Driver \
  employees \
  salaries \
  outputmlf
```