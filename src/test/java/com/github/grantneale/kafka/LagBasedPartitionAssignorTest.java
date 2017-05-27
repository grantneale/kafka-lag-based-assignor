package com.github.grantneale.kafka;

import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.core.Is.is;

import com.github.grantneale.kafka.LagBasedPartitionAssignor.TopicPartitionLag;
import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.Assert;
import org.junit.Test;


public class LagBasedPartitionAssignorTest {

    @Test
    public void testComputePartitionLag() {

        final long lag = LagBasedPartitionAssignor.computePartitionLag(
            new OffsetAndMetadata(5555),
            1111,
            9999,
            "none"
        );

        Assert.assertThat(lag, is(4444L));

    }

    /**
     * In the case where lookup of the partition begin/end offsets fails, the lag should be 0
     */
    @Test
    public void testComputePartitionLagNoEndOffset() {

        final long lag = LagBasedPartitionAssignor.computePartitionLag(
            new OffsetAndMetadata(5555),
            0,
            0,
            "none"
        );

        Assert.assertThat(lag, is(0L));

    }

    @Test
    public void testComputePartitionLagNoCommittedOffsetResetModeLatest() {

        final long lag = LagBasedPartitionAssignor.computePartitionLag(
            null,
            1111,
            9999,
            "latest"
        );

        Assert.assertThat(lag, is(0L));

    }

    @Test
    public void testComputePartitionLagNoCommittedOffsetResetModeEarliest() {

        final long beginOffset = 1111;
        final long endOffset = 9999;
        final long lag = LagBasedPartitionAssignor.computePartitionLag(
            null,
            beginOffset,
            endOffset,
            "earliest"
        );

        Assert.assertThat(lag, is(endOffset - beginOffset));

    }

    @Test
    public void testAssign() {

        final Map<String, List<TopicPartitionLag>> partitionLagPerTopic = ImmutableMap.of(
            "topic1",
            Arrays.asList(
                new TopicPartitionLag("topic1", 0, 100000),
                new TopicPartitionLag("topic1", 1, 100000),
                new TopicPartitionLag("topic1", 2, 500),
                new TopicPartitionLag("topic1", 3, 1)
            ),
            "topic2",
            Arrays.asList(
                new TopicPartitionLag("topic2", 0, 900000),
                new TopicPartitionLag("topic2", 1, 100000)
            )
        );

        final Map<String, List<String>> subscriptions = ImmutableMap.of(
            "consumer-1",
            Arrays.asList(
                "topic1",
                "topic2"
            ),
            "consumer-2",
            Arrays.asList(
                "topic1"
            )
        );

        final Map<String, List<TopicPartition>> expectedAssignment = ImmutableMap.of(
            "consumer-1",
            Arrays.asList(
                new TopicPartition("topic1", 0),
                new TopicPartition("topic1", 2),
                new TopicPartition("topic2", 0),
                new TopicPartition("topic2", 1)
            ),
            "consumer-2",
            Arrays.asList(
                new TopicPartition("topic1", 1),
                new TopicPartition("topic1", 3)
            )
        );

        final Map<String, List<TopicPartition>> actualAssignment =
            LagBasedPartitionAssignor.assign(partitionLagPerTopic, subscriptions);

        Assert.assertThat(actualAssignment.entrySet(), is(expectedAssignment.entrySet()));

    }

    @Test
    public void testAssignWithZeroLags() {

        final Map<String, List<TopicPartitionLag>> partitionLagPerTopic = ImmutableMap.of(
            "topic1",
            Arrays.asList(
                new TopicPartitionLag("topic1", 0, 0),
                new TopicPartitionLag("topic1", 1, 0),
                new TopicPartitionLag("topic1", 2, 0),
                new TopicPartitionLag("topic1", 3, 0),
                new TopicPartitionLag("topic1", 4, 0),
                new TopicPartitionLag("topic1", 5, 0),
                new TopicPartitionLag("topic1", 6, 0)
            )
        );

        final Map<String, List<String>> subscriptions = ImmutableMap.of(
            "consumer-1",
            Collections.singletonList("topic1"),
            "consumer-2",
            Collections.singletonList("topic1")
        );

        final Map<String, List<TopicPartition>> actualAssignment =
            LagBasedPartitionAssignor.assign(partitionLagPerTopic, subscriptions);

        final int maxAssignedPartitions = Collections.max(
            actualAssignment.values(),
            Comparator.comparingInt(List::size)
        ).size();

        final int minAssignedPartitions = Collections.min(
            actualAssignment.values(),
            Comparator.comparingInt(List::size)
        ).size();

        Assert.assertThat("Partitions should be distributed evenly amongst consumers",
                          maxAssignedPartitions,
                          is(lessThanOrEqualTo(minAssignedPartitions + 1))
        );

    }

    @Test
    public void testAssignWithHeavilySkewedLags() {

        // For this test, the number of partitions must NOT be divisible by the number of consumers
        final Map<String, List<TopicPartitionLag>> partitionLagPerTopic = ImmutableMap.of(
            "topic1",
            Arrays.asList(
                new TopicPartitionLag("topic1", 0, 360),
                new TopicPartitionLag("topic1", 1, 359),
                new TopicPartitionLag("topic1", 2, 230),
                new TopicPartitionLag("topic1", 3, 118),
                new TopicPartitionLag("topic1", 4, 444),
                new TopicPartitionLag("topic1", 5, 122),
                new TopicPartitionLag("topic1", 6, 65),
                new TopicPartitionLag("topic1", 7, 111),
                new TopicPartitionLag("topic1", 8, 455000),
                new TopicPartitionLag("topic1", 9, 424000)
            )
        );

        final Map<String, List<String>> subscriptions = ImmutableMap.of(
            "consumer-1",
            Collections.singletonList("topic1"),
            "consumer-2",
            Collections.singletonList("topic1"),
            "consumer-3",
            Collections.singletonList("topic1")
        );

        final Map<String, List<TopicPartition>> actualAssignment =
            LagBasedPartitionAssignor.assign(partitionLagPerTopic, subscriptions);

        final Map.Entry<String, List<TopicPartition>> consumerWithMaxPartitions = Collections.max(
            actualAssignment.entrySet(),
            Map.Entry.comparingByValue(Comparator.comparingInt(List::size))
        );
        final Map.Entry<String, List<TopicPartition>> consumerWithMinPartitions = Collections.min(
            actualAssignment.entrySet(),
            Map.Entry.comparingByValue(Comparator.comparingInt(List::size))
        );

        final int maxAssignedPartitions = consumerWithMaxPartitions.getValue().size();
        final int minAssignedPartitions = consumerWithMinPartitions.getValue().size();

        Assert.assertThat("Partitions should be distributed evenly amongst consumers",
                          maxAssignedPartitions,
                          is(lessThanOrEqualTo(minAssignedPartitions + 1))
        );

        // TODO assert that the consumer with the highest lag is assigned the equal smallest number of partitions

    }

}
