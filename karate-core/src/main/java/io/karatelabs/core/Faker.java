/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.core;

import io.karatelabs.js.Args;
import io.karatelabs.js.SimpleObject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Faker API for generating random test data.
 * Accessed via karate.faker.* in Karate scripts.
 * <p>
 * Example usage:
 * <pre>
 * * def name = karate.faker.fullName()
 * * def email = karate.faker.email()
 * * def age = karate.faker.randomInt(18, 65)
 * </pre>
 */
public class Faker implements SimpleObject {

    private static final Random RANDOM = new Random();

    private static final String[] FIRST_NAMES = {
            "Ethan", "Sophia", "Jackson", "Emma", "Liam", "Olivia",
            "Noah", "Ava", "William", "Isabella", "James", "Mia"
    };

    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia",
            "Miller", "Davis", "Rodriguez", "Martinez", "Hernandez", "Lopez"
    };

    private static final String[] CITIES = {
            "New York", "Los Angeles", "Chicago", "Houston", "Phoenix",
            "San Francisco", "Seattle", "Boston", "Denver", "Austin"
    };

    private static final String[] COUNTRIES = {
            "United States", "Canada", "United Kingdom", "Germany", "France",
            "Japan", "Australia", "Spain", "Italy", "Netherlands"
    };

    private static final String[] EMAIL_DOMAINS = {
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com", "example.com"
    };

    private static final String[] STREET_NAMES = {
            "Main Street", "Oak Avenue", "Park Road", "Cedar Lane", "Elm Street",
            "Maple Drive", "Pine Street", "Washington Boulevard", "Lake View", "Hill Road"
    };

    private static final String[] WORDS = {
            "lorem", "ipsum", "dolor", "sit", "amet", "consectetur", "adipiscing",
            "elit", "sed", "do", "eiusmod", "tempor", "incididunt", "ut", "labore"
    };

    private static final String[] COMPANY_SUFFIXES = {
            "Inc", "LLC", "Corp", "Ltd", "Group", "Solutions", "Technologies", "Systems"
    };

    private static final String[] JOB_TITLES = {
            "Software Engineer", "Product Manager", "Data Analyst", "Designer",
            "Marketing Manager", "Sales Representative", "HR Manager", "Accountant"
    };

    @Override
    public Object jsGet(String key) {
        return switch (key) {
            // Timestamps
            case "timestamp" -> Args.invoke(() -> Instant.now().getEpochSecond());
            case "timestampMs" -> Args.invoke(() -> Instant.now().toEpochMilli());
            case "isoTimestamp" -> Args.invoke((() -> Instant.now()
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_INSTANT)));

            // Names
            case "firstName" -> Args.invoke(() -> FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)]);
            case "lastName" -> Args.invoke(() -> LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)]);
            case "fullName" -> Args.invoke(() -> FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)] + " "
                    + LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)]);

            // Contact
            case "email" -> Args.invoke(() -> {
                String first = FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)].toLowerCase();
                String domain = EMAIL_DOMAINS[RANDOM.nextInt(EMAIL_DOMAINS.length)];
                return first + RANDOM.nextInt(100) + "@" + domain;
            });
            case "userName" -> Args.invoke(() -> {
                String first = FIRST_NAMES[RANDOM.nextInt(FIRST_NAMES.length)].toLowerCase();
                String last = LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)].toLowerCase();
                return first + "." + last;
            });
            case "phoneNumber" -> Args.invoke(() ->
                    "+1-555-" + String.format("%03d", RANDOM.nextInt(1000))
                            + "-" + String.format("%04d", RANDOM.nextInt(10000)));

            // Location
            case "city" -> Args.invoke(() -> CITIES[RANDOM.nextInt(CITIES.length)]);
            case "country" -> Args.invoke(() -> COUNTRIES[RANDOM.nextInt(COUNTRIES.length)]);
            case "streetAddress" -> Args.invoke(() -> (RANDOM.nextInt(9999) + 1)
                    + " " + STREET_NAMES[RANDOM.nextInt(STREET_NAMES.length)]);
            case "zipCode" -> Args.invoke(() -> String.format("%05d", RANDOM.nextInt(100000)));
            case "latitude" -> Args.invoke(() -> RANDOM.nextDouble() * 180 - 90);
            case "longitude" -> Args.invoke(() -> RANDOM.nextDouble() * 360 - 180);

            // Numbers
            case "randomInt" -> Args.invoke(args -> {
                if (args.length == 0) return RANDOM.nextInt(1001);
                if (args.length == 1) return RANDOM.nextInt(((Number) args[0]).intValue() + 1);
                int min = ((Number) args[0]).intValue();
                int max = ((Number) args[1]).intValue();
                return RANDOM.nextInt(max - min + 1) + min;
            });
            case "randomFloat" -> Args.invoke(args -> {
                if (args.length == 0) return RANDOM.nextDouble();
                if (args.length == 1) return RANDOM.nextDouble() * ((Number) args[0]).doubleValue();
                double min = ((Number) args[0]).doubleValue();
                double max = ((Number) args[1]).doubleValue();
                return RANDOM.nextDouble() * (max - min) + min;
            });

            // Boolean
            case "randomBoolean" -> Args.invoke(RANDOM::nextBoolean);

            // Text
            case "word" -> Args.invoke(() -> WORDS[RANDOM.nextInt(WORDS.length)]);
            case "sentence" -> Args.invoke(() -> {
                int wordCount = 5 + RANDOM.nextInt(10);
                StringBuilder sb = new StringBuilder();
                sb.append(Character.toUpperCase(WORDS[RANDOM.nextInt(WORDS.length)].charAt(0)));
                sb.append(WORDS[RANDOM.nextInt(WORDS.length)].substring(1));
                for (int i = 1; i < wordCount; i++) {
                    sb.append(" ").append(WORDS[RANDOM.nextInt(WORDS.length)]);
                }
                sb.append(".");
                return sb.toString();
            });
            case "paragraph" -> Args.invoke(() -> {
                int sentenceCount = 3 + RANDOM.nextInt(4);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < sentenceCount; i++) {
                    if (i > 0) sb.append(" ");
                    int wordCount = 5 + RANDOM.nextInt(10);
                    sb.append(Character.toUpperCase(WORDS[RANDOM.nextInt(WORDS.length)].charAt(0)));
                    sb.append(WORDS[RANDOM.nextInt(WORDS.length)].substring(1));
                    for (int j = 1; j < wordCount; j++) {
                        sb.append(" ").append(WORDS[RANDOM.nextInt(WORDS.length)]);
                    }
                    sb.append(".");
                }
                return sb.toString();
            });
            case "alphanumeric" -> Args.invoke(args -> {
                int length = args.length > 0 ? ((Number) args[0]).intValue() : 10;
                return generateAlphanumeric(length);
            });
            case "hexColor" -> Args.invoke(() -> String.format("#%06x", RANDOM.nextInt(0x1000000)));

            // Business
            case "companyName" -> Args.invoke(() -> {
                String name = LAST_NAMES[RANDOM.nextInt(LAST_NAMES.length)];
                String suffix = COMPANY_SUFFIXES[RANDOM.nextInt(COMPANY_SUFFIXES.length)];
                return name + " " + suffix;
            });
            case "jobTitle" -> Args.invoke(() -> JOB_TITLES[RANDOM.nextInt(JOB_TITLES.length)]);
            case "creditCardNumber" -> Args.invoke(() -> {
                // Generate fake credit card number (not valid for real transactions)
                StringBuilder sb = new StringBuilder();
                sb.append("4"); // Visa prefix
                for (int i = 1; i < 16; i++) {
                    sb.append(RANDOM.nextInt(10));
                }
                return sb.toString();
            });

            default -> null;
        };
    }

    private static String generateAlphanumeric(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

}
