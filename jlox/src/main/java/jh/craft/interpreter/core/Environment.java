package jh.craft.interpreter.core;

import jh.craft.interpreter.types.LoxError;
import jh.craft.interpreter.scanner.Token;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    private final Object NO_VALUE = new Object();
    private final Map<String, Object> values;
    private final Environment parent;

    public Environment(){
        this(null);
    }
    public Environment(Environment parent){
        this.values = new HashMap<>();
        this.parent = parent;
    }

    public void declare(Token name){
        values.put( name.lexeme(), NO_VALUE );
    }
//    public void define(Token name, Object value){
//        var identifier = name.lexeme();
//
//        if( values.containsKey( name.lexeme() ) ){
//            throw new LoxError(
//                    name, String.format("'%s' was already defined.", identifier)
//            );
//        }
//
//        values.put( identifier, value );
//    }

    public void define(String name, Object value){
        values.put( name, value );
    }


    // TODO: optimize this for globals c:
    public Object value(Token name, int scopeWalk){
        var identifier = name.lexeme();

        var env = ancestor(scopeWalk);
        var values = env.values;
        if(values.containsKey( identifier )){
            var value = values.get(identifier);
            if( value == NO_VALUE )
                throw new LoxError(
                        name, String.format("'%s' not initialized.", identifier)
                );
            return value;
        }

        throw new LoxError(
                name, String.format("'%s' not defined.", identifier)
        );
    }

    public void assign(Token name, int walk, Object value){
        var identifier = name.lexeme();

        var values = ancestor(walk).values;

        if(values.containsKey( identifier ))
            values.put( identifier , value );
        else {
            throw new LoxError(
                    name.line(), name.position(),
                    String.format("'%s' not defined.", identifier)
            );
        }
    }



    private Environment ancestor(int distance){
        Environment env = this;
        for(int i = distance; i > 0 ; i--)
            env = env.parent;
        return env;
    }
}
