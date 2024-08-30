package dev.mccue.atom.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.mccue.atom.Atom;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

public class AtomTest {
    @Test
    public void lotsOfSwaps() throws Exception {
        var count = Atom.of(0);
        var threads = new ArrayList<Thread>();
        for (int i = 0; i < 1000; i++) {
            threads.add(
                    new Thread(
                            () -> {
                                count.swap(x -> x + 1);
                            }));
        }

        for (var thread : threads) {
            thread.start();
        }

        for (var thread : threads) {
            thread.join();
        }
        assertEquals(count.get(), 1000);
    }
}
