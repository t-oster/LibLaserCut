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

import com.t_oster.liblasercut.platform.Point;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import scpsolver.constraints.LinearEqualsConstraint;
import scpsolver.constraints.LinearSmallerThanEqualsConstraint;
import scpsolver.lpsolver.LinearProgramSolver;
import scpsolver.lpsolver.SolverFactory;
import scpsolver.problems.LinearProgram;

/**
 * @author Patrick Schmidt <patrick.schmidt1@rwth-aachen.de>
 */
public class TSPOptimizer extends VectorOptimizer
{
  @Override
  protected List<Element> sort(List<Element> e)
  {
    if (e.size() <= 1)
    {
      return e;
    }

    // Use the start and end point of each element as a city
    int nCities = 2 * e.size();
    Point[] cities = new Point[nCities];
    int city_idx = 0;
    for (Element element : e)
    {
      cities[city_idx] = element.start;
      city_idx++;
      cities[city_idx] = element.getEnd();
      city_idx++;
    }
    
    // Variables:
    // booleans Xij for 0 <= i, j < n  (edge ij is part of our tour)
    // integers Ui  for 1 <= i    < n  (i is visited before j, if Ui < Uj)
    //               X_ij  for  i != j           U_i
    int nVariables = nCities*nCities - nCities + nCities - 1;
    
    System.out.println(nCities + " cities");
    System.out.println(nVariables + " variables");
    
    // Objective function: Minimize total length
    double[] objectiveCoefficients = new double[nVariables];
    for (int i = 0; i < nCities; i++)
    {
      for (int j = 0; j < nCities; j++)
      {
        if (i != j)
        {
          // TODO: Wrong for i even and j=i+1
          objectiveCoefficients[XijIndex(i, j, nCities)] = dist(cities[i], cities[j]);
        }
      }
    }
    
    LinearProgram lp = new LinearProgram(objectiveCoefficients);
    lp.setMinProblem(true);
    
    // Constrain Xij to booleans and Ui to integers
    for (int varIdx = 0; varIdx < nVariables; varIdx++)
    {
      if (varIdx < nCities * nCities - nCities)
      {
        lp.setBinary(varIdx);
      }
      else
      {
        lp.setInteger(varIdx);
      }
    }
    
    // Constraints: All cuts have to be part of the tour
    for (int i = 0; i < nCities-1; i += 2)
    {
      int j = i+1;
      double[] coeffs = new double[nVariables];
      coeffs[XijIndex(i, j, nCities)] = 1;
      lp.addConstraint(new LinearEqualsConstraint(coeffs, 1, "Include cuts"));
    }
    
    // Constraints: Each city needs one incoming and one outgoing edge
    for (int i = 0; i < nCities; i++)
    {
      double[] coeffsIn = new double[nVariables];
      double[] coeffsOut = new double[nVariables];
      
      for (int j = 0; j < nCities; j++)
      {
        if (i != j)
        {
          coeffsIn[XijIndex(j, i, nCities)] = 1;
          coeffsOut[XijIndex(i, j, nCities)] = 1;
        }
      }
      
      lp.addConstraint(new LinearEqualsConstraint(coeffsIn, 1, "One in"));
      lp.addConstraint(new LinearEqualsConstraint(coeffsOut, 1, "One out"));
    }
    
    // Constraints: The result has to be a single cycle
    for (int i = 1; i < nCities; i++)
    {
      for (int j = 1; j < nCities; j++)
      {
        if (i != j)
        {
          double[] coeffs = new double[nVariables];
          coeffs[UiIndex(i, nCities)] = 1;
          coeffs[UiIndex(j, nCities)] = -1;
          coeffs[XijIndex(i, j, nCities)] = nCities;

          lp.addConstraint(new LinearSmallerThanEqualsConstraint(coeffs, nCities - 1, "Single tour"));
        }
      }
    }
    
    System.out.println(lp.getConstraints().size() + " constraints");
    
    // Solve
    LinearProgramSolver solver = SolverFactory.newDefault();
    double[] solution = solver.solve(lp);
    
    System.out.println("Adjacency matrix:");
    for (int i = 0; i < nCities; i++)
    {
      for (int j = 0; j < nCities; j++)
      {
        if (i != j)
        {
          System.out.print((int)solution[XijIndex(i, j, nCities)] + " ");
        }
        else
        {
          System.out.print(0 + " ");
        }
      }
      System.out.print("\n");
    }
    
    // Derive resulting element order
    List<Element> result = new LinkedList<Element>();
    boolean[] citiesVisited = new boolean[nCities];
    Arrays.fill(citiesVisited, false);
    
    // Start at beginning of first element (city 0)
    Element currElement = e.get(0);
    result.add(currElement);
    citiesVisited[0] = true;
    
    // Jump to end of first element (city 1)
    int i = 1;
    citiesVisited[1] = true;
    
    boolean success = false;
    while(!success)
    {
      // Find next city of the tour
      boolean foundNextCity = false;
      for (int j = 0; j < nCities; j++)
      {
        if (i != j && !citiesVisited[j] && solution[XijIndex(i, j, nCities)] != 0)
        {
          // Found next city on the tour (city j)
          foundNextCity = true;
          int elementIndex = j / 2;
          currElement = e.get(elementIndex);
          boolean invert = (j % 2 == 1);
          int startCity = j;
          int endCity = invert ? j-1 : j+1;
          
          if (invert)
          {
            currElement.invert();
          }
          
          result.add(currElement);
          
          // Jump to the city at the end of our element
          i = endCity;
          
          // Mark both visited
          citiesVisited[startCity] = true;
          citiesVisited[endCity] = true;
          
          break;
        }
      }
      
      if (!foundNextCity)
      {
        if (allVisited(citiesVisited))
        {
          System.out.println("TSP: Found valid tour.");
          success = true;
        }
        else
        {
          System.out.println("Invalid TSP Solution: Did not find the next city from city " + i + ".");
          System.out.println("Aborting Optimization...");
          success = false;
          break;
        }
      }
    }

    if (success)
    {
      return result;
    }
    else
    {
      return new LinkedList<Element>();
    }
  }
  
  protected int XijIndex(int i, int j, int n)
  {
    assert(i != j);
    if (i < j)
    {
      // Above diagonal
      return i * (n-1) + j - 1;
    }
    else
    {
      // Below diagonal
      return i * (n-1) + j;
    }
  }
  
  protected int UiIndex(int i, int n)
  {
    return n*n - n + i - 1;
  }

  private boolean allVisited(boolean[] citiesVisited)
  {
    for (boolean visited : citiesVisited)
    {
      if (!visited)
      {
        return false;
      }
    }
    return true;
  }
}