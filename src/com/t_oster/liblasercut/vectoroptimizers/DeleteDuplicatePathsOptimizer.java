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
package com.t_oster.liblasercut.vectoroptimizers;

import java.util.LinkedList;
import java.util.List;

/**
 * This VectorOptimizer removes all duplicate (identical) Elements
 * and sorts the remaining (unique) elements with a NearestVectorOptimizer
 * @author René Bohne
 */
public class DeleteDuplicatePathsOptimizer extends VectorOptimizer
{

  @Override
  protected List<Element> sort(List<Element> e)
  {
    List<Element> result = new LinkedList<Element>();
    if (e.isEmpty())
    {
      return result;
    }
    
    List<Element> doubleEntries = new LinkedList<Element>();
    
    for(int i=0;i<e.size();i++)
    {
      for(int j=0;j<e.size();j++)
      {
        if(i!=j)
        {
         if(e.get(i).equals(e.get(j)))
         {
           if(!doubleEntries.contains(e.get(j)))
           {
            doubleEntries.add(e.get(i));
           }
         }
        }
      }
    }
    
    for(Element doubleEntry : doubleEntries)
    {
      e.remove(doubleEntry);
    }
    
    NearestVectorOptimizer vo = new NearestVectorOptimizer();
    result = vo.sort(e);
    
    return result;
  }
}
