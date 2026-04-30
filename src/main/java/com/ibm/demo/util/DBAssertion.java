package com.ibm.demo.util;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

public class DBAssertion {

    private DBAssertion() {
    } // 防止實例化

    public static void assertUpdated(int updated, Class<?> entityClass, Object id) {
        if (updated == 0) {
            throw new ObjectOptimisticLockingFailureException(entityClass, id);
        }
    }

}
