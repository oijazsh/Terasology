package org.terasology.benchmark.chunks.arrays;

import java.util.LinkedList;
import java.util.List;

import org.terasology.benchmark.Benchmark;
import org.terasology.benchmark.PrintToConsoleCallback;
import org.terasology.benchmark.Benchmarks;
import org.terasology.world.chunks.blockdata.TeraDenseArray8Bit;

/**
 * TeraArraysBenchmark simplifies the execution of the benchmarks for tera arrays.
 * 
 * @author Manuel Brotz <manu.brotz@gmx.ch>
 *
 */
@SuppressWarnings("unused")
public final class TeraArraysBenchmark {
 
    private TeraArraysBenchmark() {}
    
    private static final byte[][] INFLATED_8_BIT = new byte[256][];
    private static final byte[] DEFLATED_8_BIT = new byte[256];
    
    private static final byte[][] INFLATED_4_BIT = new byte[256][];
    private static final byte[] DEFLATED_4_BIT = new byte[256];

    static {
        for (int i = 0; i < INFLATED_8_BIT.length; i++) {
            INFLATED_8_BIT[i] = new byte[256];
        }
        for (int i = 0; i < INFLATED_4_BIT.length; i++) {
            INFLATED_4_BIT[i] = new byte[128];
        }
    }
    
    public static void main(String[] args) {

        final List<Benchmark> benchmarks = new LinkedList<Benchmark>();
        
        benchmarks.add(new BenchmarkTeraArraySerializeObject(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));
        benchmarks.add(new BenchmarkTeraArraySerializeToBuffer(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));
        benchmarks.add(new BenchmarkTeraArraySerializeToByteString(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));
        benchmarks.add(new BenchmarkTeraArraySerializeToStreamViaByteArray(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));
        benchmarks.add(new BenchmarkTeraArraySerializeToStreamViaChannel(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));

//        benchmarks.add(new BenchmarkTeraArrayDeserializeFromBuffer(new TeraDenseArray8Bit.SerializationHandler(), new TeraDenseArray8Bit(16, 256, 16)));
//
//
//        benchmarks.add(new BenchmarkTeraArrayRead(new TeraDenseArray8Bit(16, 256, 16)));
//        benchmarks.add(new BenchmarkTeraArrayRead(new TeraDenseArray4Bit(16, 256, 16)));
//        benchmarks.add(new BenchmarkTeraArrayRead(new TeraSparseArray8Bit(16, 256, 16, INFLATED_8_BIT, DEFLATED_8_BIT)));
//        benchmarks.add(new BenchmarkTeraArrayRead(new TeraSparseArray4Bit(16, 256, 16, INFLATED_4_BIT, DEFLATED_4_BIT)));
//
//
//        benchmarks.add(new BenchmarkTeraArrayWrite(new TeraDenseArray8Bit(16, 256, 16)));
//        benchmarks.add(new BenchmarkTeraArrayWrite(new TeraDenseArray4Bit(16, 256, 16)));
//        benchmarks.add(new BenchmarkTeraArrayWrite(new TeraSparseArray8Bit(16, 256, 16, INFLATED_8_BIT, DEFLATED_8_BIT)));
//        benchmarks.add(new BenchmarkTeraArrayWrite(new TeraSparseArray4Bit(16, 256, 16, INFLATED_4_BIT, DEFLATED_4_BIT)));

        Benchmarks.execute(benchmarks, new PrintToConsoleCallback());
        
    }
}
