/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package jh.craft.interpreter;

import jh.craft.interpreter.core.Lox;
import jh.craft.interpreter.errors.LoxError;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {

        if( args.length == 0)
            runPrompt();
        else if( args.length == 1 )
            runFile( args[0] );
        else  {
            System.err.println("usage: jlox <filename>");
            System.exit(1);
        }

    }

    private static void runPrompt(){
        BufferedReader reader = new BufferedReader(
                new InputStreamReader( System.in )
        );

        var ref = new Object() {
            String line = null;
        };

        var lox = new Lox( error -> printError(error, ref.line) );
        try {
            for(;;){
                System.out.print("> ");
                ref.line = reader.readLine();
                if( ref.line == null) break;
                lox.run( ref.line );
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void runFile(String filename){
        try {
            final var source = Files.readString(Path.of(filename));
            // runs the code c:
            new Lox( error -> {
                printError( error, source );
            }).run( source );
        } catch (IOException e) {
            System.out.printf(
                    "Error reading '%s': %s\n", filename, e.getMessage()
            );
            System.exit(1);
        }

    }

    private static void printError(LoxError error, String source){
        int lineStart = error.position;
        int lineEnd   = lineStart + 1;

        while( lineStart >= 0 && source.charAt( lineStart ) != '\n')
            lineStart--;

        while( lineEnd < source.length() && source.charAt( lineEnd ) != '\n')
            lineEnd++;


        // Building the errLine (
        //     The one that will have the line
        //     number and the line from the source code.
        // )
        String indication = String.format("\t %d | ", error.line);
        String errLine   =  indication + source.substring(lineStart + 1, lineEnd);

        // Calculating the number of spaces needed for ^ to be right below
        // the character where the error happened
        int errOffset = error.position - lineStart + indication.length() - 2;
        String spaces = String.format("\t%" + errOffset + "s", "");

        // Building the final String ...
        System.out.printf("Error: %s\n%s\n%s^-- Here.\n", error.msg, errLine, spaces);
    }

}
