package com.intuit.karate.junit4.demos;

import org.junit.runner.RunWith;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/demos/outline-dynamic-callonce.feature")
public class OutlineDynamicCallonceRunner {

}