package com.nicole.tfg;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;


public class Mongo7Test 
    extends TestCase
{
    public Mongo7Test( String testName )
    {
        super( testName );
    }

    public static Test suite()
    {
        return new TestSuite(Mongo7Test.class );
    }

    public void testApp()
    {
        assertTrue( true );
    }
}
