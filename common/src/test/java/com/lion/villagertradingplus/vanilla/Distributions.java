package com.lion.villagertradingplus.vanilla;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

final class Distributions {

    private Distributions() {}

    static <T> Map<String, Integer> count(int draws, Supplier<T> sampler, Function<T, List<String>> keys) {
        Map<String, Integer> counts = new HashMap<>();

        for (int i = 0; i < draws; i++) {
            T outcome = sampler.get();

            if (outcome == null) {
                counts.merge("<none>", 1, Integer::sum);
                continue;
            }

            for (String key : keys.apply(outcome)) {
                counts.merge(key, 1, Integer::sum);
            }
        }

        return counts;
    }

    static String compare(Map<String, Integer> vanilla, Map<String, Integer> actual) {
        Set<String> all = new HashSet<>(vanilla.keySet());
        all.addAll(actual.keySet());

        double chi = 0;
        int df = 0;
        int rareVanilla = 0;
        int rareActual = 0;
        String worst = "";
        double worstChi = -1;

        for (String key : all) {
            int a = vanilla.getOrDefault(key, 0);
            int b = actual.getOrDefault(key, 0);

            if ((a == 0 && b >= 15) || (b == 0 && a >= 15)) {
                return "outcome only one side produces: " + key + " vanilla=" + a + " actual=" + b;
            }

            if (a + b < 40) {
                rareVanilla += a;
                rareActual += b;
                continue;
            }

            double part = (double) (a - b) * (a - b) / (a + b);
            chi += part;
            df++;

            if (part > worstChi) {
                worstChi = part;
                worst = key + " vanilla=" + a + " actual=" + b;
            }
        }

        if (rareVanilla + rareActual >= 40) {
            chi += (double) (rareVanilla - rareActual) * (rareVanilla - rareActual) / (rareVanilla + rareActual);
            df++;
        }

        double limit = df + 5 * Math.sqrt(2.0 * df) + 10;

        if (chi <= limit) {
            return null;
        }

        return "chi-square " + chi + " over " + df + " classes exceeds " + limit + "; worst " + worst;
    }
}
