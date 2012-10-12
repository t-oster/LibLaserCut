package com.t_oster.liblasercut;

/**
 *
 * @author thommy
 */
public interface Customizable {
    public String[] getPropertyKeys();
    /**
     * Sets the property with the given key
     * a property may only be of the classes
     * Integer, Boolean, Double, Float and String
     * and never set to null
     * @param key
     * @param value 
     */
    public void setProperty(String key, Object value);
    /**
     * Returns the value of the property or null, if the key
     * does not name a valid property
     * A property may never return null!
     * @param key
     * @return 
     */
    public Object getProperty(String key);
}
