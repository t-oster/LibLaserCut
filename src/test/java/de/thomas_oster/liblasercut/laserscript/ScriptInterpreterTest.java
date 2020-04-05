/**
 * This file is part of LibLaserCut.
 * Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>
 *
 * LibLaserCut is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibLaserCut is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.
 *
 **/
package de.thomas_oster.liblasercut.laserscript;

import de.thomas_oster.liblasercut.PowerSpeedFocusProperty;
import de.thomas_oster.liblasercut.VectorPart;
import de.thomas_oster.liblasercut.laserscript.ScriptInterface;
import de.thomas_oster.liblasercut.laserscript.ScriptInterpreter;
import de.thomas_oster.liblasercut.laserscript.VectorPartScriptInterface;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import javax.script.ScriptException;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Thomas Oster <thomas.oster@rwth-aachen.de>
 */
public class ScriptInterpreterTest
{
  
  public ScriptInterpreterTest()
  {
  }
  
  @Test
  public void testSecurity() throws ScriptException, IOException
  {
    SecurityManager bak = System.getSecurityManager();
    ScriptInterpreter instance = new ScriptInterpreter();
    File f = new File("testfile");
    if (f.exists())
    {
      f.delete();
    }
    try
    {
      instance.execute(new InputStreamReader(this.getClass().getResourceAsStream("SecurityTest.js")), new ScriptInterface(){

        public void move(double x, double y)
        {
        }

        public void line(double x, double y)
        {
        }

        public void set(String property, Object value)
        {
        }

        public Object get(String property)
        {
          return null;
        }

        public void echo(String text)
        {
        }

        @Override
        public String prompt(String title, String defaultValue)
        {
          return null;
        }
      });
    }
    catch (ScriptException e)
    {
      assertFalse(f.exists());
      return;
    }
    fail("No Exception thrown");
  }

  @Test 
  public void testVectorJobScriptInterface() throws ScriptException, IOException
  {
    String script = "function rectangle(x, y, width, height) {";
    script += "move(x, y);";
    script += "line(x+width, y);";
    script += "line(x+width, y+height);";
    script += "line(x, y+height);";
    script += "line(x, y);";
    script += "}";
    script += "rectangle(0, 0, 20, 30);";
    script += "rectangle(0, 0, 20, 30);";
    ScriptInterpreter instance = new ScriptInterpreter();
    VectorPart vp = new VectorPart(new PowerSpeedFocusProperty(), 500d);
    VectorPartScriptInterface si = new VectorPartScriptInterface(vp, new AffineTransform());
    instance.execute(script, si);
    assertEquals(0, vp.getMinX(), 1e-99);
    assertEquals(0, vp.getMinY(), 1e-99);
    assertEquals(20, vp.getMaxX(), 1e-99);
    assertEquals(30, vp.getMaxY(), 1e-99);
  }
  
  /**
   * Test of execute method, of class ScriptInterpreter.
   */
  @Test
  public void testExecute() throws Exception
  {
    System.out.println("execute");
    String script = "function rectangle(x, y, width, height) {";
    script += "move(x, y);";
    script += "line(x+width, y);";
    script += "line(x+width, y+height);";
    script += "line(x, y+height);";
    script += "line(x, y);";
    script += "}";
    script += "rectangle(0, 0, 20, 30);";
    script += "rectangle(0, 0, 20, 30);";
    ScriptInterpreter instance = new ScriptInterpreter();
    final List<String> steps = new LinkedList<>();
    instance.execute(script, new ScriptInterface(){

      public void move(double x, double y)
      {
        assertTrue(x == 0 && y == 0);
        steps.add("move ("+x+","+y+")");
      }

      public void line(double x, double y)
      {
         steps.add("line ("+x+","+y+")");
      }

      public void set(String property, Object value)
      {
          steps.add("set ("+property+","+value.toString()+")");
      }

      public Object get(String property)
      {
        steps.add("get ("+property+")");
        return null;
      }

      public void echo(String text)
      {
        steps.add("echo ("+text+")");
      }

      @Override
      public String prompt(String title, String defaultValue)
      {
        steps.add("prompt(" + title + "','" + defaultValue + "')");
        return null;
      }
    });
    assertEquals(10, steps.size());
  }
}
