-- ============================================================================
-- Flyway Migration V1: Initial Schema
-- ============================================================================
-- This migration creates the initial database schema for the application.
-- Compatible with both H2 (Oracle mode) and Oracle databases.
--
-- Tables:
-- 1. ACCOUNT - User account information
-- 2. PRODUCT - Product catalog
-- 3. ORDER_INFO - Order header information
-- 4. ORDER_PRODUCT_DETAIL - Order line items
--
-- All tables include:
-- - Soft delete support (DELETED flag)
-- - Audit timestamps (CREATED_AT, UPDATED_AT, DELETED_AT)
-- - Optimistic locking (VERSION)
-- ============================================================================

-- ============================================================================
-- ACCOUNT Table
-- ============================================================================
CREATE TABLE ACCOUNT (
    ID INTEGER NOT NULL,
    NAME NVARCHAR2(50) NOT NULL,
    STATUS VARCHAR2(1) NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED BOOLEAN DEFAULT FALSE,
    DELETED_AT TIMESTAMP,
    VERSION INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ACCOUNT PRIMARY KEY (ID)
);

CREATE SEQUENCE account_id_seq START WITH 1 INCREMENT BY 1;

-- ============================================================================
-- PRODUCT Table
-- ============================================================================
CREATE TABLE PRODUCT (
    ID INTEGER NOT NULL,
    NAME NVARCHAR2(100) NOT NULL,
    PRICE DECIMAL(12,4) NOT NULL,
    SALE_STATUS INTEGER NOT NULL,
    AVAILABLE INTEGER DEFAULT 0 NOT NULL,
    RESERVED INTEGER DEFAULT 0 NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED BOOLEAN DEFAULT FALSE,
    DELETED_AT TIMESTAMP,
    VERSION INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT PK_PRODUCT PRIMARY KEY (ID)
);

CREATE SEQUENCE product_id_seq START WITH 1 INCREMENT BY 1;

-- ============================================================================
-- ORDER_INFO Table
-- ============================================================================
CREATE TABLE ORDER_INFO (
    ID INTEGER NOT NULL,
    ACCOUNT_ID INTEGER NOT NULL,
    STATUS INTEGER NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED BOOLEAN DEFAULT FALSE,
    DELETED_AT TIMESTAMP,
    VERSION INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ORDER_INFO PRIMARY KEY (ID),
    CONSTRAINT FK_ORDER_ACCOUNT FOREIGN KEY (ACCOUNT_ID) REFERENCES ACCOUNT(ID)
);

CREATE SEQUENCE order_id_seq START WITH 1 INCREMENT BY 1;

-- ============================================================================
-- ORDER_PRODUCT_DETAIL Table
-- ============================================================================
CREATE TABLE ORDER_PRODUCT_DETAIL (
    ID INTEGER NOT NULL,
    ORDER_ID INTEGER NOT NULL,
    PRODUCT_ID INTEGER NOT NULL,
    QUANTITY INTEGER NOT NULL,
    CREATED_AT TIMESTAMP,
    UPDATED_AT TIMESTAMP,
    DELETED BOOLEAN DEFAULT FALSE,
    DELETED_AT TIMESTAMP,
    VERSION INTEGER DEFAULT 0 NOT NULL,
    CONSTRAINT PK_ORDER_DETAIL PRIMARY KEY (ID),
    CONSTRAINT FK_DETAIL_ORDER FOREIGN KEY (ORDER_ID) REFERENCES ORDER_INFO(ID),
    CONSTRAINT FK_DETAIL_PRODUCT FOREIGN KEY (PRODUCT_ID) REFERENCES PRODUCT(ID)
);

CREATE SEQUENCE order_product_detail_id_seq START WITH 1 INCREMENT BY 1;

-- ============================================================================
-- End of Migration V1
-- ============================================================================