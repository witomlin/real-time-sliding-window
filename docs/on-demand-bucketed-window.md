# On Demand Bucketed Window
<!-- TOC -->
* [On Demand Bucketed Window](#on-demand-bucketed-window)
  * [Configuration](#configuration)
    * [Task Scheduler](#task-scheduler)
  * [Usage](#usage)
    * [Adding Data](#adding-data)
    * [Retrieving Data](#retrieving-data)
      * [Entire Window (Single Bucket)](#entire-window-single-bucket)
      * [Custom](#custom)
  * [Bucket Metadata](#bucket-metadata)
  * [Bucket Data Metadata](#bucket-data-metadata)
  * [Events](#events)
  * [Metrics](#metrics)
<!-- TOC -->

![](diagrams/on-demand-how-it-works.png)

## Configuration
Use `OnDemandBucketedWindowConfig` to configure the window. The window must be explicitly started after creating.

```kotlin
val windowConfig = OnDemandBucketedWindowConfig(
  name = "on-demand-window",
  length = Duration.ofSeconds(30),
  forDataClasses = listOf(MyData::class, MyOtherData::class),
  taskScheduler = DefaultTaskScheduler(),
  metrics = MicrometerMetrics(myMeterRegistry),
)

val window = OnDemandBucketedWindow(windowConfig).apply { start() }
```

| Parameter             | Type              | Description                                                                         |
|:----------------------|:------------------|:------------------------------------------------------------------------------------|
| `name`                | `String`          | The name of the window. Must be unique if multiple windows are created.             |
| `length`              | `Duration`        | The length of the window. Minimum 250ms.                                            |
| `forDataClasses`      | `List<KClass<*>>` | A list of data classes that the window will store.                                  |
| `taskScheduler`       | `TaskScheduler`   | The task scheduler to use. See [below](#task-scheduler).                            |
| `metrics`             | `Metrics`         | The metrics implementation to use (`MicrometerMetrics` or `NoOpMetrics`).           |
| `maintenanceInterval` | `Duration`        | The frequency at which to perform window maintenance. See [below](#task-scheduler). |

### Task Scheduler
The task scheduler is used to schedule a periodic window maintenance task to remove data items older than the window
length.

`DefaultTaskScheduler` provides a `ScheduledThreadPoolExecutor`-based implementation, but you can provide your own
alternative implementation if desired.

## Usage
### Adding Data
To add data to the window:

```kotlin
window.addData(MyData())
window.addData(MyOtherData())
```

### Retrieving Data
Retrieve a view of the data in the window via the `onDemandTumblingBuckets` method.

> [!NOTE]
> The `onDemandTumblingBuckets` method builds a view dynamically and its performance is therefore dependent on the
> number of data classes stored in the window, the number of items and the arguments supplied to the method.

#### Entire Window (Single Bucket)
To retrieve a single bucket view of all data contained within the window:

```kotlin
val bucket = window.onDemandTumblingBuckets()[0] // No arguments defaults to the entire window and single bucket
val bucketMyData = bucket.dataForClass(MyData::class)
val bucketMyOtherData = bucket.dataForClass(MyOtherData::class)

bucketMyData.entries.forEach { timestampedMyData -> println(timestampedMyData) }
bucketMyOtherData.entries.forEach { timestampedMyOtherData -> println(timestampedMyOtherData) }
```

`bucketMyData` and `bucketMyOtherData` are immutable.

#### Custom
To retrieve a view of the window with a custom start point, length and bucket length:

```kotlin
val buckets = window.onDemandTumblingBuckets(
    start = Instant.now().minusSeconds(20),
    length = Duration.ofSeconds(10),
    bucketLength = Duration.ofSeconds(2) // Produces 5 buckets
)

buckets.forEach { bucket ->
    val bucketMyData = bucket.dataForClass(MyData::class) // Immutable
    val bucketMyOtherData = bucket.dataForClass(MyOtherData::class) // Immutable

    bucketMyData.entries.forEach { timestampedMyData -> println(timestampedMyData) }
    bucketMyOtherData.entries.forEach { timestampedMyOtherData -> println(timestampedMyOtherData) }
}
```

`bucketMyData` and `bucketMyOtherData` are immutable.

## Bucket Metadata
The following properties are made available for each `OnDemandBucketedWindowBucket`:

| Property            | Type         | Description                                       |
|:--------------------|:-------------|:--------------------------------------------------|
| `type`              | `BucketType` | The type of bucket. Currently always `TUMBLING`.  |
| `start`             | `Instant`    | The start time of the bucket.                     |
| `end`               | `Instant`    | The end time of the bucket.                       |
| `isMutationAllowed` | `Boolean`    | Whether data mutation is allowed. Always `false`. |


## Bucket Data Metadata
See [here](fixed-tumbling-bucketed-window.md#bucket-data-metadata).

## Events
No events are emitted by this window.

## Metrics
The following window metrics are available when [metrics are enabled](#configuration):

| Name                                            | Type                | Tags                   | Description                                                                         |
|:------------------------------------------------|:--------------------|:-----------------------|:------------------------------------------------------------------------------------|
| `window_config_length_ms`                       | Gauge               | `window_name`          | The configured window length.                                                       |
| `window_on_demand_tumbling_buckets_duration_ms` | Timer               | `window_name`          | The duration of the `onDemandTumblingBuckets` method.                               |
| `window_maintenance_duration_ms`                | Timer               | `window_name`          | The duration when processing the end of the current bucket.                         |
| `window_data_item_count`                        | DistributionSummary | `window_name`, `class` | The number of data items in the entire window (all non-current buckets), per class. |
