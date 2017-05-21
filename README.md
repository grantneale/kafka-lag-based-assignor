# kafka-lag-based-assignor
Kafka partition assignor that distributes lag evenly across a consumer group.

Requires Kafka 0.10.0.0 or later.

## Overview

The LagBasedPartitionAssignor operates on a per-topic basis, and attempts to assign partitions such that lag is
distributed evenly across a consumer group.

For each topic, we first obtain the lag on all partitions.  Lag on a given partition is the difference between the
end offset and the last offset committed by the consumer group.  If no offsets have been committed for a partition we
determine the lag based on the `code auto.offset.reset` property.  If `auto.offset.reset=latest`, we assign a
lag of 0.  If `auto.offset.reset=earliest` (or any other value) we assume assign lag equal to the total number
of message currently available in that partition.

We then create a map storing the current total lag of all partitions assigned to each member of the consumer group.
Partitions are assigned in decreasing order of lag, with each partition assigned to the consumer with least total
lag.

### Example: LagBasedPartitionAssignor

For example, suppose there are two consumers `C0` and `C1`, both subscribed to a topic `t0` having 3 partitions with the
following lags:

    t0p0: 100,000
    t0p1:  50,000
    t0p2:  60,000

The assignment will be:

    C0: [t0p0]
    C1: [t0p1, t0p2]

The total lag or partitions assigned to each consumer will be:

    C0: 100,000
    C1: 110,000

### Example: RangeAssignor (kafka default)

Compare this to the assignments made by Kafka's default `org.apache.kafka.clients.consumer.RangeAssignor`:

    C0: [t0p0, t0p1]
    C1: [t0p2]

The RangeAssignor results in a less balanced total lag for each consumer of:

    C0: 160,000
    C1:  50,000
