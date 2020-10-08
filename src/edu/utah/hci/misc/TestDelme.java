package edu.utah.hci.misc;

import java.util.Scanner;

public class TestDelme {

	
	public TestDelme(){


	}
	

	
	
	public static void main(String[] args) {


	        Scanner s = new Scanner(System.in);
	        System.out.print("Enter the first number: ");
	        Float a = s.nextFloat();
	        System.out.print("Enter the second number: ");
	        Float b = s.nextFloat();
	        System.out.println("Sum = " + (a+b));
	        System.out.println("Difference = " + (a-b));
	        System.out.println("Product = " + (a*b));
	        s.close();
	        
	        String name = s.nextLine();


	    

	}

}
