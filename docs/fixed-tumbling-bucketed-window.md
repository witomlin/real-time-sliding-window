# Fixed Tumbling Bucketed Window
> [!IMPORTANT]  
> Please consider [the limitations](#limitations) when deciding if this implementation is right for your requirements.

<!-- TOC -->
* [Fixed Tumbling Bucketed Window](#fixed-tumbling-bucketed-window)
  * [Configuration](#configuration)
    * [Task Scheduler](#task-scheduler)
  * [Usage](#usage)
    * [Adding Data](#adding-data)
    * [Retrieving Current Bucket Data](#retrieving-current-bucket-data)
    * [Retrieving Non-Current Bucket Data](#retrieving-non-current-bucket-data)
    * [Retrieving Non-Current and Current Bucket Data](#retrieving-non-current-and-current-bucket-data)
  * [Bucket Metadata](#bucket-metadata)
  * [Bucket Data Metadata](#bucket-data-metadata)
    * [Events](#events)
  * [Metrics](#metrics)
  * [Limitations](#limitations)
<!-- TOC -->

![](diagrams/fixed-how-it-works.png)

## Configuration
Use `FixedTumblingBucketedWindowConfig` to configure the window. The window must be explicitly started after creating.

```kotlin
val windowConfig = FixedTumblingBucketedWindowConfig(
  name = "fixed-window",
  length = Duration.ofSeconds(30),
  bucketLength = Duration.ofSeconds(5),
  forDataClasses = listOf(MyData::class, MyOtherData::class),
  taskScheduler = DefaultTaskScheduler(),
  metrics = MicrometerMetrics(myMeterRegistry),
)

val window = FixedTumblingBucketedWindow(windowConfig).apply { start() }
```

| Parameter        | Type              | Description                                                                                             |
|:-----------------|:------------------|:--------------------------------------------------------------------------------------------------------|
| `name`           | `String`          | The name of the window. Must be unique if multiple windows are created.                                 |
| `length`         | `Duration`        | The length of the window. Minimum 250ms.                                                                |
| `bucketLength`   | `Duration`        | The length of each bucket within the window. Minimum 250ms. `length` must be an exact multiple of this. |
| `forDataClasses` | `List<KClass<*>>` | A list of data classes that the window will store.                                                      |
| `taskScheduler`  | `TaskScheduler`   | The task scheduler to use for scheduling bucket ends. See [below](#task-scheduler).                     |
| `metrics`        | `Metrics`         | The metrics implementation to use (`MicrometerMetrics` or `NoOpMetrics`).                               |

### Task Scheduler
The task scheduler is used to:
- Invoke a handler upon the current bucket reaching its scheduled end time.
- Notify observers of certain [events](#events) asynchronously.
  
`DefaultTaskScheduler` provides a `ScheduledThreadPoolExecutor`-based implementation, but you can provide your own
alternative implementation if desired.

> [!CAUTION]
> An accurate, high resolution underlying scheduler is crucial for the proper functioning of the window.

## Usage
### Adding Data
To add data to the current bucket:

```kotlin
window.addData(MyData())
window.addData(MyOtherData())
```

### Retrieving Current Bucket Data
To retrieve the current bucket and the data contained within it:

```kotlin
val currentBucket = window.currentBucket // Note: may become non-current
val currentBucketMyData = currentBucket.dataForClass(MyData::class)
val currentBucketMyOtherData = currentBucket.dataForClass(MyOtherData::class)

currentBucketMyData.entries.forEach { timestampedMyData -> println(timestampedMyData) }
currentBucketMyOtherData.entries.forEach { timestampedMyOtherData -> println(timestampedMyOtherData) }
```

Note: `entries` returns both the data and the timestamp indicating when the data was added.

`currentBucketMyData` and `currentBucketMyOtherData` are mutable only if `isMutationAllowed`; the current bucket will
eventually become non-current. You should normally add data via the `window.addData` [method](#adding-data).

```kotlin
if (currentBucketMyData.isMutationAllowed) currentBucketMyData.add(MyData())
if (currentBucketMyOtherData.isMutationAllowed) currentBucketMyOtherData.add(MyOtherData())
```

### Retrieving Non-Current Bucket Data
To retrieve all non-current buckets (the entire window) and the data contained within it:

```kotlin
val nonCurrentBuckets = window.nonCurrentBuckets // Note: is a snapshot at the time of retrieval

nonCurrentBuckets.forEach { bucket ->
    val bucketMyData = bucket.dataForClass(MyData::class) // Immutable
    val bucketMyOtherData = bucket.dataForClass(MyOtherData::class) // Immutable

    bucketMyData.entries.forEach { timestampedMyData -> println(timestampedMyData) }
    bucketMyOtherData.entries.forEach { timestampedMyOtherData -> println(timestampedMyOtherData) }
}
```

`bucketMyData` and `bucketMyOtherData` are immutable.

### Retrieving Non-Current and Current Bucket Data
To retrieve all buckets:

```kotlin
val currentBucket = window.buckets
```

## Bucket Metadata
The following properties are made available for each `FixedTumblingBucketedWindowBucket`:

| Property            | Type         | Description                                                 |
|:--------------------|:-------------|:------------------------------------------------------------|
| `status`            | `Status`     | The status of the bucket: `CURRENT` or `NON_CURRENT`.       |
| `type`              | `BucketType` | The type of bucket. Always `TUMBLING`.                      |
| `start`             | `Instant`    | The start time of the bucket.                               |
| `scheduledEnd`      | `Instant`    | The scheduled end time of the bucket.                       |
| `endInfo`           | `EndInfo?`   | The end information of the bucket. Only populated upon end. |
| `isMutationAllowed` | `Boolean`    | Whether data mutation is allowed.                           |

`EndInfo`:

| Property         | Type      | Description                                                                      |
|:-----------------|:----------|:---------------------------------------------------------------------------------|
| `actualEnd`      | `Instant` | The actual end time of the bucket after any scheduling and/or processing delays. |
| `durationMillis` | `Int`     | The actual duration of the bucket in milliseconds.                               |

## Bucket Data Metadata
The following properties are made available for each `BucketData`:

| Property              | Type                       | Description                       |
|:----------------------|:---------------------------|:----------------------------------|
| `entries`             | `List<TimestampedData<T>>` | The timestamped data items.       |
| `size`                | `Int`                      | The number of data items.         |
| `firstEntryTimestamp` | `Instant?`                 | The timestamp of the first entry. |
| `lastEntryTimestamp`  | `Instant?`                 | The timestamp of the last entry.  |
| `isMutationAllowed`   | `Boolean`                  | Whether data mutation is allowed. |

### Events
The following events are emitted by the window:

| Event Name                                                   | Description                                                        |
|:-------------------------------------------------------------|:-------------------------------------------------------------------|
| `FixedTumblingBucketedWindow.Event.CurrentBucketStarted`     | The current bucket has started.                                    |
| `FixedTumblingBucketedWindow.Event.CurrentBucketEnding`      | The current bucket is about to end (but hasn't yet).               |
| `FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated` | Non-current buckets (i.e. the entire window) have been updated.    |
| `FixedTumblingBucketedWindow.Event.NonCurrentBucketRemoving` | A non-current bucket is about to be removed (but hasn't been yet). |

Observe one or more of these events as follows:

```kotlin
val window = FixedTumblingBucketedWindow(windowConfig).apply {
    addObserver(::windowEvent)
    start()
}

private fun windowEvent(event: FixedTumblingBucketedWindow.Event, buckets: List<FixedTumblingBucketedWindowBucket>) {
    if (event == FixedTumblingBucketedWindow.Event.CurrentBucketStarted)
        println("Current bucket started: ${buckets[0]}")
    
    if (event == FixedTumblingBucketedWindow.Event.CurrentBucketEnding)
        println("Current bucket ending: ${buckets[0]}")
    
    if (event == FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated)
        println("Non-current buckets updated: $buckets")

    if (event == FixedTumblingBucketedWindow.Event.NonCurrentBucketRemoving)
        println("Non-current bucket removing: $buckets[0]")
}
```
> [!NOTE]
> Except for `FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated`, observers are notified sequentially and
> synchronously when the window processes the end of the current bucket.

> [!CAUTION]
> Except for `FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated`, do not perform long-running operations
> within event handlers as this may elongate bucket durations and/or block read/write operations performed via the
> window. Offload any long-running operations to a separate thread.

`FixedTumblingBucketedWindow.Event.CurrentBucketStarted` and `FixedTumblingBucketedWindow.Event.CurrentBucketEnding`
can be used, for example, to add data at the beginning and end of the current bucket. If doing this, do not offload the
operation to another thread as the bucket may be immutable by the time the data is added.

`FixedTumblingBucketedWindow.Event.NonCurrentBucketsUpdated` can be used, for example, to do something using data
within the entire window or individual non-current buckets, once window data is updated. All received buckets are
immutable.

`FixedTumblingBucketedWindow.Event.NonCurrentBucketRemoving` can be used, for example, to remove data from a cache that
was calculated using individual non-current buckets. The received bucket is immutable.

## Metrics
The following window metrics are available when [metrics are enabled](#configuration):

| Name                             | Type  | Tags                   | Description                                                                                                          |
|:---------------------------------|:------|:-----------------------|:---------------------------------------------------------------------------------------------------------------------|
| `window.config.length_ms`        | Gauge | `window_name`          | The configured window length.                                                                                        |
| `window.config.bucket.length_ms` | Gauge | `window_name`          | The configured window bucket length.                                                                                 |
| `window.data.items`              | Gauge | `window_name`, `class` | The number of data items in the entire window (all non-current buckets), per class. Updated upon current bucket end. |
| `window.bucket.duration`         | Timer | `window_name`          | The actual current bucket duration. Updated upon current bucket end.                                                 |
| `window.observer.duration`       | Timer | `window_name`, `event` | The cumulative duration of all observers, per event.                                                                 |
| `window.maintenance.duration`    | Timer | `window_name`          | The duration when processing the end of the current bucket.                                                          |

## Limitations
> [!IMPORTANT]  
> `FixedTumblingBucketedWindow` should not be used when high precision is required - consider using the
> [On Demand Bucketed Window](on-demand-bucketed-window.md) instead.

Through its nature and design, `FixedTumblingBucketedWindow` relies upon accurate task scheduling and timely processing
when a current bucket ends. Because of this, the bucket durations (and therefore window length) are **approximate**
and may vary slightly from the supplied configuration, depending on conditions.

The following conditions may affect the accuracy of the window:
- [Task scheduler](#task-scheduler) accuracy, resolution or thread exhaustion.
- Long-running operations within [event handlers](#events).
- CPU contention, exhaustion or throttling.
- Garbage collection.

Use the [metrics](#metrics) provided by the window to monitor the window's accuracy, in particular:
- `window.bucket.duration`
- `window.observer.duration`
- `window.maintenance.duration`

When a current bucket ends, a `WARN` will be logged if its actual duration is more than 110% of its intended duration.
