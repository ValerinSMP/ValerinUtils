import sqlite3

conn = sqlite3.connect(r'C:\Users\marti\Desktop\pruebas\plugins\RoyaleEconomy\database\royaleEconomyData.db')
cursor = conn.cursor()

# Listar tablas
cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
tables = cursor.fetchall()

# Ver estructura de TODAS las tablas
print("=== ESTRUCTURA DE TODAS LAS TABLAS ===")
for table in tables:
    table_name = table[0]
    cursor.execute(f"PRAGMA table_info({table_name})")
    columns = cursor.fetchall()
    col_names = [c[1] for c in columns]
    print(f"\n{table_name}: {col_names}")

# Ver datos de PlayerPurse
print("\n\n=== DATOS DE PlayerPurse ===")
cursor.execute("SELECT * FROM PlayerPurse LIMIT 5")
rows = cursor.fetchall()
for row in rows:
    print(row)

conn.close()
