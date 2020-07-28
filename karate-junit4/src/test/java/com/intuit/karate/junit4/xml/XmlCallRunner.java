package com.intuit.karate.junit4.xml;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.junit4.Karate;
import org.junit.runner.RunWith;

@RunWith(Karate.class)
@KarateOptions(features = "classpath:com/intuit/karate/junit4/xml/xml-call.feature")
public class XmlCallRunner {

}