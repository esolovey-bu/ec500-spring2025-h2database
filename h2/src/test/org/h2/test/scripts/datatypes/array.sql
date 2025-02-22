-- Copyright 2004-2025 H2 Group. Multiple-Licensed under the MPL 2.0,
-- and the EPL 1.0 (https://h2database.com/html/license.html).
-- Initial Developer: H2 Group
--

SELECT (10, 20, 30)[1];
> exception INVALID_VALUE_2

SELECT ARRAY[];
>> []

SELECT ARRAY[10];
>> [10]

SELECT ARRAY[10, 20, 30];
>> [10, 20, 30]

SELECT ARRAY[10, 20, 30][1];
>> 10

SELECT ARRAY[10, 20, 30][3];
>> 30

SELECT ARRAY[10, 20, 30][0];
> exception ARRAY_ELEMENT_ERROR_2

SELECT ARRAY[10, 20, 30][4];
> exception ARRAY_ELEMENT_ERROR_2

SELECT ARRAY[1, NULL] IS NOT DISTINCT FROM ARRAY[1, NULL];
>> TRUE

SELECT ARRAY[1, NULL] IS DISTINCT FROM ARRAY[1, NULL];
>> FALSE

SELECT ARRAY[1, NULL] = ARRAY[1, NULL];
>> null

SELECT ARRAY[1, NULL] <> ARRAY[1, NULL];
>> null

SELECT ARRAY[NULL] = ARRAY[NULL, NULL];
>> FALSE

select ARRAY[1, NULL, 2] = ARRAY[1, NULL, 1];
>> FALSE

select ARRAY[1, NULL, 2] <> ARRAY[1, NULL, 1];
>> TRUE

SELECT ARRAY[1, NULL] > ARRAY[1, NULL];
>> null

SELECT ARRAY[1, 2] > ARRAY[1, NULL];
>> null

SELECT ARRAY[1, 2, NULL] > ARRAY[1, 1, NULL];
>> TRUE

SELECT ARRAY[1, 1, NULL] > ARRAY[1, 2, NULL];
>> FALSE

SELECT ARRAY[1, 2, NULL] < ARRAY[1, 1, NULL];
>> FALSE

SELECT ARRAY[1, 1, NULL] <= ARRAY[1, 1, NULL];
>> null

SELECT ARRAY[1, NULL] IN (ARRAY[1, NULL]);
>> null

CREATE TABLE TEST(A ARRAY);
> exception SYNTAX_ERROR_2

CREATE TABLE TEST(A INTEGER ARRAY);
> ok

INSERT INTO TEST VALUES (ARRAY[1, NULL]), (ARRAY[1, 2]);
> update count: 2

SELECT ARRAY[1, 2] IN (SELECT A FROM TEST);
>> TRUE

SELECT ROW (ARRAY[1, 2]) IN (SELECT A FROM TEST);
>> TRUE

SELECT ARRAY[1, NULL] IN (SELECT A FROM TEST);
>> null

SELECT ROW (ARRAY[1, NULL]) IN (SELECT A FROM TEST);
>> null

SELECT A FROM TEST WHERE A = (1, 2);
> exception TYPES_ARE_NOT_COMPARABLE_2

DROP TABLE TEST;
> ok

SELECT ARRAY[1, 2] || 3;
>> [1, 2, 3]

SELECT 1 || ARRAY[2, 3];
>> [1, 2, 3]

SELECT ARRAY[1, 2] || ARRAY[3];
>> [1, 2, 3]

SELECT ARRAY[1, 2] || ARRAY[3, 4];
>> [1, 2, 3, 4]

SELECT ARRAY[1, 2] || NULL;
>> null

SELECT NULL::INT ARRAY || ARRAY[2];
>> null

CREATE TABLE TEST(ID INT, A1 INT ARRAY, A2 INT ARRAY[2]);
> ok

SELECT COLUMN_NAME, DATA_TYPE, MAXIMUM_CARDINALITY
    FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'TEST' ORDER BY ORDINAL_POSITION;
> COLUMN_NAME DATA_TYPE MAXIMUM_CARDINALITY
> ----------- --------- -------------------
> ID          INTEGER   null
> A1          ARRAY     65536
> A2          ARRAY     2
> rows (ordered): 3

INSERT INTO TEST VALUES (1, ARRAY[], ARRAY[]), (2, ARRAY[1, 2], ARRAY[1, 2]);
> update count: 2

INSERT INTO TEST VALUES (3, ARRAY[], ARRAY[1, 2, 3]);
> exception VALUE_TOO_LONG_2

TABLE TEST;
> ID A1     A2
> -- ------ ------
> 1  []     []
> 2  [1, 2] [1, 2]
> rows: 2

DROP TABLE TEST;
> ok

CREATE MEMORY TABLE TEST(A1 INT ARRAY, A2 INT ARRAY[2], A3 INT ARRAY[0]);
> ok

SCRIPT NODATA NOPASSWORDS NOSETTINGS NOVERSION TABLE TEST;
> SCRIPT
> --------------------------------------------------------------------------------------------------------
> CREATE USER IF NOT EXISTS "SA" PASSWORD '' ADMIN;
> CREATE MEMORY TABLE "PUBLIC"."TEST"( "A1" INTEGER ARRAY, "A2" INTEGER ARRAY[2], "A3" INTEGER ARRAY[0] );
> -- 0 +/- SELECT COUNT(*) FROM PUBLIC.TEST;
> rows (ordered): 3

INSERT INTO TEST(A3) VALUES ARRAY[NULL];
> exception VALUE_TOO_LONG_2

DROP TABLE TEST;
> ok

CREATE MEMORY TABLE TEST1(I INT ARRAY, I2 INT ARRAY[2]);
> ok

INSERT INTO TEST1 VALUES (ARRAY[1, 2, 3.0], ARRAY[1, NULL]);
> update count: 1

@reconnect

TABLE TEST1;
> I         I2
> --------- ---------
> [1, 2, 3] [1, null]
> rows: 1

INSERT INTO TEST1 VALUES (ARRAY[], ARRAY['abc']);
> exception DATA_CONVERSION_ERROR_1

CREATE MEMORY TABLE TEST2 AS (TABLE TEST1) WITH NO DATA;
> ok

CREATE MEMORY TABLE TEST3(A TIME ARRAY[10] ARRAY[2]);
> ok

INSERT INTO TEST3 VALUES ARRAY[ARRAY[TIME '10:00:00']];
> update count: 1

SCRIPT NOPASSWORDS NOSETTINGS NOVERSION;
> SCRIPT
> ---------------------------------------------------------------------------------
> CREATE USER IF NOT EXISTS "SA" PASSWORD '' ADMIN;
> CREATE MEMORY TABLE "PUBLIC"."TEST1"( "I" INTEGER ARRAY, "I2" INTEGER ARRAY[2] );
> -- 1 +/- SELECT COUNT(*) FROM PUBLIC.TEST1;
> INSERT INTO "PUBLIC"."TEST1" VALUES (ARRAY [1, 2, 3], ARRAY [1, NULL]);
> CREATE MEMORY TABLE "PUBLIC"."TEST2"( "I" INTEGER ARRAY, "I2" INTEGER ARRAY[2] );
> -- 0 +/- SELECT COUNT(*) FROM PUBLIC.TEST2;
> CREATE MEMORY TABLE "PUBLIC"."TEST3"( "A" TIME ARRAY[10] ARRAY[2] );
> -- 1 +/- SELECT COUNT(*) FROM PUBLIC.TEST3;
> INSERT INTO "PUBLIC"."TEST3" VALUES (ARRAY [ARRAY [TIME '10:00:00']]);
> rows (ordered): 9

DROP TABLE TEST1, TEST2, TEST3;
> ok

VALUES CAST(ARRAY['1', '2'] AS DOUBLE PRECISION ARRAY);
>> [1.0, 2.0]

EXPLAIN VALUES CAST(ARRAY['1', '2'] AS DOUBLE PRECISION ARRAY);
>> VALUES (CAST(ARRAY [1.0, 2.0] AS DOUBLE PRECISION ARRAY))

CREATE TABLE TEST(A1 TIMESTAMP ARRAY, A2 TIMESTAMP ARRAY ARRAY);
> ok

CREATE INDEX IDX3 ON TEST(A1);
> ok

CREATE INDEX IDX4 ON TEST(A2);
> ok

DROP TABLE TEST;
> ok

VALUES CAST(ARRAY[ARRAY[1, 2], ARRAY[3, 4]] AS INT ARRAY[2] ARRAY[1]);
>> [[1, 2]]

VALUES CAST(ARRAY[ARRAY[1, 2], ARRAY[3, 4]] AS INT ARRAY[1] ARRAY[2]);
>> [[1], [3]]

VALUES CAST(ARRAY[1, 2] AS INT ARRAY[0]);
>> []

VALUES ARRAY??(1??);
>> [1]

EXPLAIN VALUES ARRAY??(1, 2??);
>> VALUES (ARRAY [1, 2])

VALUES ARRAY(SELECT X FROM SYSTEM_RANGE(1, 10));
>> [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]

CREATE TABLE TEST AS VALUES ARRAY(SELECT X FROM SYSTEM_RANGE(1, 1) WHERE FALSE) WITH NO DATA;
> ok

SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'TEST';
>> ARRAY

SELECT DATA_TYPE FROM INFORMATION_SCHEMA.ELEMENT_TYPES WHERE OBJECT_NAME = 'TEST';
>> BIGINT

DROP TABLE TEST;
> ok

VALUES ARRAY(SELECT);
> exception SUBQUERY_IS_NOT_SINGLE_COLUMN

VALUES ARRAY(SELECT 1, 2);
> exception SUBQUERY_IS_NOT_SINGLE_COLUMN

EXPLAIN VALUES ARRAY[NULL, 1, '3'];
>> VALUES (ARRAY [NULL, 1, 3])

CREATE TABLE TEST(A INTEGER ARRAY[65536]);
> ok

DROP TABLE TEST;
> ok

CREATE TABLE TEST(A INTEGER ARRAY[65537]);
> exception INVALID_VALUE_PRECISION

SELECT ARRAY[ARRAY[3, 4], ARRAY[5, 6]][1][2];
>> 4
