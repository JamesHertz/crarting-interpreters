
package jh.craft.interpreter.representation;

import jh.craft.interpreter.scanner.Token;
import java.util.List;

public interface Stmt {

    interface Visitor<T> {
        T visitExpression( Expression expression );
        T visitPrint( Print print );
        T visitVar( Var var );
        T visitBlock( Block block );
    }

    <T> T accept( Visitor<T> visitor );

    record Expression( Expr expression ) implements Stmt {
        @Override
        public <T> T accept( Visitor<T> visitor ){ 
            return visitor.visitExpression( this );
        }
    }

    record Print( Expr expression ) implements Stmt {
        @Override
        public <T> T accept( Visitor<T> visitor ){ 
            return visitor.visitPrint( this );
        }
    }

    record Var( Token name, Expr initializer ) implements Stmt {
        @Override
        public <T> T accept( Visitor<T> visitor ){ 
            return visitor.visitVar( this );
        }
    }

    record Block( List<Stmt> body ) implements Stmt {
        @Override
        public <T> T accept( Visitor<T> visitor ){ 
            return visitor.visitBlock( this );
        }
    }

}