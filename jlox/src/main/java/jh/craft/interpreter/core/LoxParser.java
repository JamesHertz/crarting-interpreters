package jh.craft.interpreter.core;

import jh.craft.interpreter.types.*;
import jh.craft.interpreter.ast.Expr;
import jh.craft.interpreter.ast.Stmt;
import jh.craft.interpreter.scanner.Token;
import jh.craft.interpreter.scanner.TokenType;

import static jh.craft.interpreter.scanner.TokenType.*;

import java.util.ArrayList;
import java.util.List;

public class LoxParser {
    private static final int MAX_PARAMETERS = 255;

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
                reporter.report(error);
                synchronize();
            }
        }
        return stmts;
    }

    private Stmt declaration(){
        if(match(CLASS)) return classDecl();
        if(match(VAR)) return varDecl();
        if(!checkNext(LEFT_PAREN) && match(FUN))
            return funDecl();
        return statement();
    }

    private Stmt classDecl() {
        var name = consume(IDENTIFIER, "Expected an class name after 'class' keyword.");
        Token parent = null;

        if( match(LESS) )
            parent = consume(IDENTIFIER, "Expected super class name.");

        consume(LEFT_BRACE, "Expected '{' before class body.");

        var methods = new ArrayList<Stmt.FunctionDecl>();
        while(!check(RIGHT_BRACE) && !isAtEnd()){
            // TODO: look at this c:
            methods.add( funDecl() );
        }
        consume(RIGHT_BRACE, "Expected enclosing '}' after class declaration.");

        return new Stmt.ClassDecl(name, parent, methods);
    }

    private Stmt.FunctionDecl funDecl(){
        var name = consume(IDENTIFIER, "Expected function/method identifier.");
        var sig = funSignature();
        return new Stmt.FunctionDecl(
                name, sig.parameters(), sig.body()
        );
    }

    private Expr.AnonymousFun funSignature(){
        consume(LEFT_PAREN, "Expected '(' after function/method identifier.");

        var parameters = new ArrayList<Token>();
        if( !check(RIGHT_PAREN) ){
            do{
                if(parameters.size() > MAX_PARAMETERS){
                    throw new LoxError(peek(), "Can't have more than " + MAX_PARAMETERS + " parameters.");
                }
                parameters.add(
                        consume(IDENTIFIER, "Expected parameter identifier.")
                );
            }while(match(COMMA));
        }

        consume(RIGHT_PAREN, "Expected enclosing ')' after parameters.");
        consume(LEFT_BRACE, "Expected '{' before function body.");
        var body = block();

        return new Expr.AnonymousFun(
                parameters, body
        );
    }


    private Stmt varDecl(){
        consume(IDENTIFIER, "Expected an variable identifier.");
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
        if(match(WHILE)) return whileStatement();
        if(match(FOR)) return forStatement();
        if(match(RETURN)) return returnStatement();
        return expressionStatement();
    }

    private Stmt returnStatement() {
        var stmt = new Stmt.ReturnStmt(
                previous(), check(SEMICOLON) ? null : expression()
        );

        consume(SEMICOLON, "Expected ';' after return value.");
        return stmt;
    }

    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expected '(' after for.");

        Stmt initializer = null;
        if(match(VAR))
            initializer = varDecl();
        else if(!match(SEMICOLON))
            initializer = expressionStatement();

        Expr condition = null;
        if(!check(SEMICOLON))
            condition = expression();
        consume(SEMICOLON, "Expected ';' after the loop condition.");

        Expr increment = null;
        if(!check(RIGHT_PAREN))
            increment = expression();
        consume(RIGHT_PAREN, "Expected enclosing ')'.");

        Stmt body = statement();
        if( increment != null ){
            body = new Stmt.Block(List.of(
                    body, new Stmt.Expression( increment )
            ));
        }

        if( condition == null )
            condition = new Expr.Literal( true );

        Stmt result = new Stmt.WhileStmt(
               condition, body
        );

        if( initializer != null ){
            result = new Stmt.Block(List.of(
                    initializer, result
            ));
        }

        return result;
    }

    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expected '(' after while.");
        var condition = expression();
        consume(RIGHT_PAREN, "Expected enclosing ')'.");
        var body = statement();

        return new Stmt.WhileStmt( condition, body );
    }

    private Stmt ifStatement(){
        consume(LEFT_PAREN, "Expected '(' after 'if' keyword.");
        var condition = expression();
        consume(RIGHT_PAREN, "Expected enclosing ')'.");
        var body = statement();

        return new Stmt.IfStmt(
                condition, body, match(ELSE) ? statement() : null
        );
    }

    private Stmt blockStatement(){
        return new Stmt.Block( block() );
    }

    private List<Stmt> block(){
        List<Stmt> body = new ArrayList<>();
        while( !check(RIGHT_BRACE) && !isAtEnd() ){
            body.add( declaration() );
        }
        consume(RIGHT_BRACE, "Expected a '}'.");
        return body;
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
        var expr = or();
        if(match(
                EQUAL,
                SLASH_EQUAL, PLUS_EQUAL,
                MINUS_EQUAL, STAR_EQUAL
        )){
            var op = this.previous();
            var value = parseLeftSide(
                    expr, op, assigment()
            );

            if(expr instanceof Expr.Variable variable ){
                return new Expr.Assign(
                        variable.name(), value
                );
            } else if(expr instanceof Expr.Get get){
                return new Expr.Set(
                        get.expression(), get.property(), value
                );
            }

            reporter.report(new LoxError(
                    op, "Invalid assigment target."
            ));
        }

        return expr;
    }

    private Expr parseLeftSide(Expr left, Token operator, Expr right){
        return switch (operator.type()){
            case PLUS_EQUAL -> new Expr.Binary(
                    left, Token.from(operator, PLUS), right
            );

            case SLASH_EQUAL -> new Expr.Binary(
                    left, Token.from(operator, SLASH), right
            );

            case MINUS_EQUAL -> new Expr.Binary(
                    left, Token.from(operator, MINUS), right
            );

            case STAR_EQUAL -> new Expr.Binary(
                    left, Token.from(operator, STAR), right
            );

            // case EQUAL ...
            default -> right;
        };
    }


    private Expr or() {
        var expr = and();

        while(match(OR)){
            var op = previous();
            var right = and();
            expr = new Expr.Logical(
                   expr, op, right
            );
        }

        return expr;
    }

    private Expr and() {
        var expr = equality();

        while(match(AND)){
            var op = previous();
            var right = equality();
            expr = new Expr.Logical(
                    expr, op, right
            );
        }

        return expr;
    }

    private Expr equality(){
        var expr = comparison();

        while(match( BANG_EQUAL, EQUAL_EQUAL )){
            var operator = previous();
            var right = comparison();
            expr = new Expr.Binary(expr, operator, right);
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

        return call();
    }

    private Expr call(){
        var expr = primary();
        while(true) {
            if(match(LEFT_PAREN)){
                expr = endCall(expr);
            } else if(match(DOT)){
                var property = consume(
                        IDENTIFIER, "Expected an identifier after '.'"
                );
                expr = new Expr.Get(
                        expr, property
                );
            } else
                break;
        }

        return expr;
    }


    private Expr endCall(Expr expr){
        var arguments = new ArrayList<Expr>();

        if( !check(RIGHT_PAREN) ){
            do{
                if(arguments.size() > MAX_PARAMETERS){
                    throw new LoxError(peek(), "Can't have more than " + MAX_PARAMETERS + " arguments");
                }
                arguments.add( expression() );
            }while(match(COMMA));
        }

        var paren = consume(RIGHT_PAREN, "Expected enclosing ')' after arguments.");
        return new Expr.Call(
                expr, paren, arguments
        );
    }


    private Expr primary(){
        var token = advance();
        return switch (token.type()){
            case NUMBER, STRING -> new Expr.Literal( token.literal() );
            case NIL   -> new Expr.Literal( null );
            case TRUE  -> new Expr.Literal( true );
            case FALSE -> new Expr.Literal( false );
            case THIS  -> new Expr.ThisExpr( previous() );
            case LEFT_PAREN -> {
                var expr = expression();
                if( match( RIGHT_PAREN ) ){
                    yield new Expr.Grouping( expr );
                }
                throw error("Expected enclosing ')'.");
            }
            case IDENTIFIER -> new Expr.Variable( token );
            case FUN -> funSignature(); // parse anonymous function
            case SUPER -> {
                var keyword = previous();
                consume(DOT, "Expected a '.' after 'super' keyword");
                yield new Expr.SuperExpr(
                        keyword, consume(IDENTIFIER, "Expected an identifier.")
                );
            }

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

    public boolean checkNext(TokenType type) {
       if( isAtEnd() )
           return false;
       var target = current + 1;
       return target < tokens.size() && tokens.get(target).type() == type;
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

            // TODO: think about when we get a this in the middle of a class c:
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

    private Token consume(TokenType type, String msg){
        var token = peek();
        if( token.type() != type ){
            throw new LoxError(
                    token.line(), token.position(), msg
            );
        }
        return advance();
    }

    private boolean isAtEnd(){
        return peek().type() == EOF;
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token advance(){
        if(isAtEnd())
            return peek();
       current++;
       return previous();
    }

    private Token previous(){
        return tokens.get( current - 1 );
    }

}
