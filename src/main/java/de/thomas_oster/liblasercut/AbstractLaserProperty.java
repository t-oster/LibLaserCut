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

package de.thomas_oster.liblasercut;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * Delegates all the LaserProperties to a map. The minimum and maximum and
 * possible values elements are stored and masked behind suffixes in the map.
 */
public class AbstractLaserProperty implements LaserProperty
{

  static final String MAX_SUFFIX = "_maximum";
  static final String MIN_SUFFIX = "_minimum";
  static final String VALUES_SUFFIX = "_values";
  Map<String, Object> properties = new HashMap<String, Object>();

  public AbstractLaserProperty()
  {
  }

  public AbstractLaserProperty(AbstractLaserProperty p)
  {
    properties = new HashMap<String, Object>(p.properties);
  }

  final public void addPropertyRanged(String name, Object value, Object min, Object max)
  {
    properties.put(name, value);
    properties.put(name + MIN_SUFFIX, min);
    properties.put(name + MAX_SUFFIX, max);
  }
  
  final public void addProperty(String name, Object value)
  {
    properties.put(name, value);
  }
  
  final public void addPropertySpecific(String name, Object... values) {
    properties.put(name, values[0]);
    properties.put(name + VALUES_SUFFIX, values);
  }
  
  @Override
  public Object getMinimumValue(String name)
  {
    return properties.getOrDefault(name + MIN_SUFFIX, null);
  }

  @Override
  public Object getMaximumValue(String name)
  {
    return properties.getOrDefault(name + MAX_SUFFIX, null);
  }

  @Override
  public Object[] getPossibleValues(String name)
  {
    Object values = properties.getOrDefault(name + VALUES_SUFFIX, null);
    if (values == null)
    {
      return null;
    }
    if (values instanceof Object[])
    {
      return (Object[]) values;
    }
    if (values instanceof List)
    {
      List<Object> list = (List<Object>) values;
      return list.toArray();
    }
    return null;
  }

  @Override
  public LaserProperty clone()
  {
    return new AbstractLaserProperty(this);
  }

  @Override
  public String[] getPropertyKeys()
  {
    Set<String> keys = properties.keySet();
    ArrayList<String> list = new ArrayList<String>();
    for (String key : keys)
    {
      if ((!key.endsWith(MIN_SUFFIX))
        && (!key.endsWith(MAX_SUFFIX))
        && (!key.endsWith(VALUES_SUFFIX)))
      {
        list.add(key);
      }
    }
    String[] k = new String[list.size()];
    k = list.toArray(k);
    return k;
  }

  @Override
  public void setProperty(String key, Object value)
  {
    properties.put(key, value);
  }

  @Override
  public Object getProperty(String key)
  {
    return properties.get(key);
  }
  
  public boolean hasProperty(String key) {
    return properties.containsKey(key);
  }

  public boolean containsKey(Object key)
  {
    return properties.containsKey(key);
  }

  public Set<Map.Entry<String, Object>> entrySet()
  {
    return properties.entrySet();
  }

  public Object getOrDefault(Object key, Object defaultValue)
  {
    return properties.getOrDefault(key, defaultValue);
  }

  public Double getDouble(String key)
  {
    return getDouble(key, null);
  }

  public Double getDouble(String key, Double def)
  {
    Object obj = properties.getOrDefault(key, def);
    if (obj instanceof Double)
    {
      return (Double) obj;
    }
    return def;
  }

  public Float getFloat(String key)
  {
    return getFloat(key, null);
  }

  public Float getFloat(String key, Float def)
  {
    Object obj = properties.getOrDefault(key, def);
    if (obj instanceof Float)
    {
      return (Float) obj;
    }
    return def;
  }

  public Integer getInteger(String key)
  {
    return getInteger(key, null);
  }

  public Integer getInteger(String key, Integer def)
  {
    Object obj = properties.getOrDefault(key, def);
    if (obj instanceof Integer)
    {
      return (Integer) obj;
    }
    return def;
  }
  
  /**
   * get value in numeric datatype, if available
   * @param key
   * @return numeric value converted to Double; 0 if not present or a non-numeric datatype
   */
  public double getNumeric(String key)
  {
    if (getDouble(key) != null) {
      return getDouble(key);
    }
    if (getFloat(key) != null) {
      return getFloat(key);
    }
    if (getInteger(key) != null) {
      return getInteger(key);
    }
    return 0;
  }
  
  /**
   * set existing property to numeric value, automatically converting to the used datatype.
   * A warning is printed if the key does not exist.
   * @param key
   * @param value 
   */
  public void setNumeric(String key, double value)
  {
    if (getDouble(key) != null) {
      this.setProperty(key, (Double) value);
      return;
    }
    if (getFloat(key) != null) {
      this.setProperty(key, (Float) (float) value);
      return;
    }
    if (getInteger(key) != null) {
      this.setProperty(key, (Integer) (int) value);
      return;
    }
    Logger.getLogger(this.getClass().getName()).warning("tried to set nonexistent property " + key);
  }
  
  public Boolean getBoolean(String key)
  {
    return getBoolean(key, null);
  }

  public Boolean getBoolean(String key, Boolean def)
  {
    Object obj = properties.getOrDefault(key, def);
    if (obj instanceof Boolean)
    {
      return (Boolean) obj;
    }
    return def;
  }

  public String getString(String key)
  {
    return getString(key, null);
  }

  public String getString(String key, String def)
  {
    Object obj = properties.getOrDefault(key, def);
    if (obj instanceof String)
    {
      return (String) obj;
    }
    return def;
  }

  @Override
  public int hashCode()
  {
    return properties.hashCode();
  }

  @Override
  public boolean equals(Object obj)
  {
    if (this == obj)
    {
      return true;
    }
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    final AbstractLaserProperty other = (AbstractLaserProperty) obj;
    if (!Objects.equals(this.properties, other.properties))
    {
      return false;
    }
    return true;
  }

  @Override
  public float getPower()
  {
    return (float) getNumeric("power");
  }

  @Override
  public void setPower(float p)
  {
    setNumeric("power", p);
  }

  @Override
  public float getSpeed()
  {
    return (float) getNumeric("speed");
  }

}
