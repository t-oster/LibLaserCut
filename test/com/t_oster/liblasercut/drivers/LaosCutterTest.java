package com.t_oster.liblasercut.drivers;

import com.t_oster.liblasercut.IllegalJobException;
import com.t_oster.liblasercut.LaserJob;
import com.t_oster.liblasercut.LaserProperty;
import com.t_oster.liblasercut.VectorPart;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class LaosCutterTest
{
  
  public LaosCutterTest()
  {
  }

  @BeforeClass
  public static void setUpClass() throws Exception
  {
  }

  @AfterClass
  public static void tearDownClass() throws Exception
  {
  }

  @Test
  public void testSomeMethod() throws IllegalJobException, Exception
  {
    LaosCutter lc = new LaosCutter();
    lc.setUseTftp(true);
    lc.setHostname("192.168.2.111");
    lc.setPort(2000);
    VectorPart vp = new VectorPart(new LaserProperty());
    vp.moveto(100,100);
    vp.lineto(200, 100);
    LaserJob job = new LaserJob("bla", "bla", "bla", 500, null, vp, null);
    lc.sendJob(job);
  }
}
