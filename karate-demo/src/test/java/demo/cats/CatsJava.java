/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
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
package demo.cats;

import com.intuit.karate.demo.domain.Cat;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author pthomas3
 */
public class CatsJava {

    /*
    {
      name: 'Billie',
      kittens: [
          { id: 23, name: 'Bob' },
          { id: 42, name: 'Wild' }
      ]
    }    
     */
    @Test
    public void testMatchingUsingPojos() {

        Cat billie = new Cat();
        billie.setName("Billie");
        Cat bob = new Cat();
        bob.setId(23);
        bob.setName("Bob");
        billie.addKitten(bob);
        Cat wild = new Cat();
        wild.setId(42);
        wild.setName("Wild");
        billie.addKitten(wild);

        // * match billie.kittens contains { id: 42, name: 'Wild' }
        boolean found = false;
        if (billie.getKittens() != null) {
            for (Cat kitten : billie.getKittens()) {
                if (kitten.getId() == 42 && "Wild".equals(kitten.getName())) {
                    found = true;
                }
            }
        }
        assertTrue(found);
        
        Cat test = new Cat();
        test.setId(42);
        test.setName("Wild");
        
        assertTrue(hasKitten(billie, test));
        
    }

    private static boolean hasKitten(Cat cat, Cat kitten) {
        if (cat.getKittens() != null) {
            for (Cat kit : cat.getKittens()) {
                if (kit.getId() == kitten.getId()) {
                    if (kit.getName() == null) {
                        if (kitten.getName() == null) {
                            return true;
                        }
                    } else if (kit.getName().equals(kitten.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

}
