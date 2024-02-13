package jh.craft.interpreter.core;

import jh.craft.interpreter.errors.*;
import jh.craft.interpreter.representation.Expr;
import jh.craft.interpreter.representation.Stmt;
import jh.craft.interpreter.scanner.Token;
import jh.craft.interpreter.scanner.TokenType;

import static jh.craft.interpreter.scanner.TokenType.*;

import java.util.ArrayList;
import java.util.List;

public class LoxParser {

    private final List<Token> tokens;
    private final LoxErrorReporter reporter;
    private int current;

    public LoxParser(List<Token> tokens, LoxErrorReporter reporter){
        this.tokens = tokens;
        this.reporter = reporter;
        this.current = 0;
    }

    public List<Stmt> parse(){
        var stmts = new ArrayList<Stmt>();
        while(!isAtEnd()){
            try{
                stmts.add( declaration() );
            }catch (LoxError error){
                reporter.error(error);
                synchronize();
            }
        }
        return stmts;
    }

    private Stmt declaration(){
        if(match(VAR)) return varDecl();
        return statement();
    }


    private Stmt varDecl(){
        consume(IDENTIFIER, "Expected an variable indentifier");
        var name = previous();

        Expr initializer = null;
        if(match(EQUAL)) 
            initializer = expression();

        consume(SEMICOLON, "Expected ';' after value.");

        return new Stmt.Var( name, initializer );
    }


    private Stmt statement(){
        if(match(PRINT)) return printStatement();
        if(match(LEFT_BRACE)) return blockStatement();
        if(match(IF)) return ifStatement();
        return expressionStatement();
    }

    private Stmt ifStatement(){
        consume(LEFT_PAREN, "Expected '(' after if.");
        var condition = expression();
        consume(RIGHT_PAREN, "Expected ')'.");
        var body = statement();

        return new Stmt.IfStmt(
                condition, body, match(ELSE) ? statement() : null
        );
    }

    private Stmt blockStatement(){
        List<Stmt> body = new ArrayList<>();
        while( !isAtEnd() && !check(RIGHT_BRACE) ){
            body.add( declaration() );
        }

        consume(RIGHT_BRACE, "Expected a '}'.");
        return new Stmt.Block( body );
    }

    private Stmt printStatement(){
        var statement = new Stmt.Print(
                expression()
        );

        consume(SEMICOLON, "Expected ';' after expression.");
        return statement;
    }

    private Stmt expressionStatement(){
        var statement = new Stmt.Expression(
                expression()
        );

        consume(SEMICOLON, "Expected ';' after expression.");
        return statement;
    }

    private Expr expression(){
        return assigment();
    }

    private Expr assigment(){
        var expr = equality();
        if(match(EQUAL)){
            if( !(expr instanceof Expr.Variable variable) ){
                var token = this.previous();
                throw new LoxError(
                        token, "Expected a identifier at the left side of an assigment."
                );
            }

            expr = new Expr.Assign(
                    variable.name(), equality()
            );
        }

        return expr;
    }


    private Expr equality(){
        var expr = comparison();

        while(match( BANG_EQUAL, EQUAL_EQUAL )){
            var operator = previous();
            var second = comparison();
            expr = new Expr.Binary(expr, operator, second);
        }

        return expr;
    }

    private Expr comparison(){
        var expr = term();

        while (match( GREATER, GREATER_EQUAL, LESS, LESS_EQUAL )){
            var operator = previous();
            var second = comparison();
            expr = new Expr.Binary(expr, operator, second);
        }

        return expr;
    }

    private Expr term(){
        var expr = factor();

        while( match(PLUS, MINUS) ){
            var operator = previous();
            var second = term();
            expr = new Expr.Binary(expr, operator, second);
        }

        return expr;
    }

    private Expr factor(){
        var expr = unary();

        while(match(SLASH, STAR)){
            var operator = previous();
            var second = factor();
            expr = new Expr.Binary(expr, operator, second);
        }

        return expr;
    }

    private Expr unary() {

        if(match(MINUS, BANG)){
            var operator = previous();
            var second = unary();
            return new Expr.Unary(operator, second);
        }

        return primary();
    }

    private Expr primary(){
        var token = advance();
        return switch (token.type()){
            case NUMBER, STRING -> new Expr.Literal( token.literal() );
            case NIL   -> new Expr.Literal( null );
            case TRUE  -> new Expr.Literal( true );
            case FALSE -> new Expr.Literal( false );
            case LEFT_PAREN -> {
                var expr = expression();
                if( match( RIGHT_PAREN ) ){
                    yield new Expr.Grouping( expr );
                }
                throw error("Expected ')' :c");
            }
            case IDENTIFIER -> new Expr.Variable( token );
            default -> {
                throw error("Expected an expression :c");
            }
        };
    }

    public LoxError error(String msg){
        var token = previous();
        return new LoxError(
                token.line(), token.position(), msg
        );
    }

    public boolean match(TokenType...types){
        for( var t : types ){
            if( check(t) ){
                advance();
                return true;
            }
        }
        return false;
    }

    public boolean check(TokenType type){
        if( isAtEnd() )
            return false;
        return peek().type() == type;
    }

    // The idea is to skip enough tokens until we
    // get to a token that starts a new statement
    // this way we can report several syntax errors
    // at once.
    private void synchronize(){
        advance(); // skip error token c:

        while(!isAtEnd()){
            if(previous().type() == SEMICOLON )
                return;

            switch (peek().type()){
                case CLASS:
                case IF:
                case VAR:
                case FOR:
                case WHILE:
                case FUN:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private void consume(TokenType type, String msg){
        var token = peek();
        if( token.type() != type ){
            throw new LoxError(
                    token.line(), token.position(), msg
            );
        }
        advance();
    }

    private boolean isAtEnd(){
        return peek().type() == EOF;
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token advance(){
        if(!isAtEnd()) current++;
        return previous();
    }

    private Token previous(){
        return tokens.get( current - 1 );
    }

}