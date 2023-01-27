

from pyspark import SparkContext, SparkConf
from pyspark.sql import SQLContext, SparkSession
import pyspark.sql.functions as F

# Spark SQL Session
ss = SparkSession.builder \
    .getOrCreate()
#.config(conf=conf) \


# Query configuration only (cannot pass any ES conf here :-( )
es_query_conf= {
    "pushdown": True
}

# Zeppelin calls the spark session "spark
# ss is perhaps politically unsure but is really much more standard
#ss = spark

# Every time there is a shuffle, Spark needs to decide how many partitions will
# the shuffle RDD have.
# 2 times the amount of CPUS in the cluster is a good value (default is 200)
ss.conf.set("spark.sql.shuffle.partitions", "12")

# Read transactions dataset
trans_df = ss.read \
    .format("org.elasticsearch.spark.sql") \
    .options(conf=es_query_conf) \
    .load("berka-trans") \
    .alias("trans_df")

# remap columns
trans_type_mapping = {
    'PRIJEM': 'credit',
    'VYDAJ': 'withdrawal',
    'VYBER': 'withdrawal'
}

trans_operation_mapping = {
    'VYBER KARTOU': 'credit card withdrawal',
    'VKLAD': 'credit in cash',
    'PREVOD Z UCTU': 'collection from another bank',
    'VYBER': ' withdrawal in cash',
    'PREVOD NA UCET': 'remittance to another bank',
}

trans_k_symbol_mapping = {
    'POJISTNE': 'insurrance payment',
    'SLUZBY': 'payment for statement',
    'UROK': 'interest credited',
    'SANKC. UROK': 'sanction interest if negative balance',
    'SIPO': 'household',
    'DUCHOD': 'old-age pension',
    'UVER': 'loan payment'
}

trans_df = trans_df \
    .replace(to_replace=trans_type_mapping, subset=['type']) \
    .replace(to_replace=trans_operation_mapping, subset=['operation']) \
    .replace(to_replace=trans_k_symbol_mapping, subset=['k_symbol'])


# 1. Joining on Account
# ---------------------------------------------------------

# Read account dataset
account_df = ss.read \
    .format("org.elasticsearch.spark.sql") \
    .options(conf=es_query_conf) \
    .load("berka-account") \
    .alias("account_df")

# remap column frequency
account_frequency_mapping = {
    'POPLATEK MESICNE': 'monthly issuance',
    'POPLATEK TYDNE': 'weekly issuance',
    'POPLATEK PO OBRATU': 'issuance after transaction'
}

account_df = account_df.replace(to_replace=account_frequency_mapping, subset=['frequency'])

# Join on account_id
account_joint_df = trans_df \
    .join( \
    account_df, \
    (F.lower(trans_df.account_id) == F.lower(account_df.account_id)), \
    "left_outer" \
    ) \
    .select( \
    'trans_id', 'trans_df.account_id', \
    F.col("trans_df.@timestamp").alias("@timestamp"), \
    F.col("trans_df.@timestamp").alias("value_date"), \
    F.col("type").alias("transaction_type"), \
    'operation', \
    "amount", \
    "balance", \
    'k_symbol', \
    F.col("bank").alias("counterparty_bank"), F.col("account").alias("counterparty_account"), \
    F.col("district_id").alias("account_district_id"), \
    F.col("frequency").alias("account_frequency"), \
    F.col("creation_date").alias("account_opening_date") \
    )


# 2. Joining on Disp
# ---------------------------------------------------------

# Read disp dataset
disp_df = ss.read \
    .format("org.elasticsearch.spark.sql") \
    .options(conf=es_query_conf) \
    .load("berka-disp") \
    .alias("disp_df") \
    .filter(F.col ("type") == "OWNER")

disp_joint_df = account_joint_df \
    .join( \
    disp_df, \
    (F.lower(account_joint_df.account_id) == F.lower(disp_df.account_id)), \
    "left_outer" \
    ) \
    .select( \
    'trans_id', F.col ('trans_df.account_id').alias ('account_id'), "@timestamp", \
    'value_date', 'transaction_type', 'operation', \
    "amount", \
    "balance", "account_opening_date", \
    'k_symbol', 'counterparty_bank', 'counterparty_account', \
    'account_district_id', 'account_frequency', \
    'disp_id', 'client_id', F.col("disp_df.type").alias("disp_access_type")
)


# 3. Joining on Client
# ---------------------------------------------------------

# Read client dataset
client_df = ss.read \
    .format("org.elasticsearch.spark.sql") \
    .options(conf=es_query_conf) \
    .load("berka-client") \
    .alias("client_df")

client_joint_df = disp_joint_df \
    .join( \
    client_df, \
    (F.lower(disp_joint_df.client_id) == F.lower(client_df.client_id)), \
    "left_outer" \
    ) \
    .select( \
    'trans_id', 'account_id', '@timestamp', 'value_date', 'transaction_type', 'operation', 'amount', \
    'balance', 'k_symbol', 'counterparty_bank', 'counterparty_account', \
    'account_district_id', 'disp_id', 'account_frequency', "account_opening_date",
    F.col ('client_df.client_id').alias ('client_id'),
    'disp_access_type',
    F.col ("birth_number").alias("client_birth_number"),
    F.col ("district_id").alias("client_district_id")
)




# 4. Joining on District
# ---------------------------------------------------------

# Read district dataset
district_df = ss.read \
    .format("org.elasticsearch.spark.sql") \
    .options(conf=es_query_conf) \
    .load("berka-district") \
    .alias("district_df")

district_joint_df = client_joint_df \
    .join( \
    district_df, \
    (F.lower(client_joint_df.client_district_id) == F.lower(district_df.district_id)), \
    "left_outer" \
    ) \
    .select( \
    'trans_id', 'account_id', \
    F.date_format(F.col('@timestamp'), "yyyy-MM-dd'T'HH:mm:ssZZ").alias('@timestamp'), \
    F.date_format(F.col('value_date'), "yyyy-MM-dd'T'HH:mm:ssZZ").alias('value_date'), \
    'transaction_type', 'operation', \
    'amount', 'balance', 'k_symbol', 'counterparty_bank', 'counterparty_account', \
    'account_district_id', 'disp_id', 'account_frequency', 'client_id', "account_opening_date",
    'disp_access_type', 'client_birth_number', 'client_district_id',
    F.col ("district_name").alias("client_district_name"),
    F.col ("region_name").alias("client_region_name"),
)


# 5. Save transactions
# ---------------------------------------------------------


# # (3) Collect result to the driver
# join_transactions_list = district_joint_df.collect()
#
# print ("Printing 10 first results")
# for x in join_transactions_list[0:10]:
#     print x
#
# # Print count
# print ("Computed %s positions (from collected list)") % len (join_transactions_list)


district_joint_df.write \
    .format("org.elasticsearch.spark.sql") \
    .option("es.resource", "berka-transactions") \
    .mode(saveMode="Overwrite") \
    .save()


# 6. Filter in payments only
# ---------------------------------------------------------

payment_df = district_joint_df.filter( (F.col("transaction_type") == "withdrawal"))

payment_df.write \
    .format("org.elasticsearch.spark.sql") \
    .option("es.resource", "berka-payments") \
    .mode(saveMode="Overwrite") \
    .save()


