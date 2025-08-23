#!/bin/bash

# Fix the merchant exclusion states to properly exclude large/test transactions

echo "🔧 Fixing merchant exclusion states..."

# Create updated exclusion JSON with problematic merchants set to false
EXCLUSIONS='{
  "PRAGATHI HARDWARE AND ELECTRICALS": true,
  "SWIGGY": true,
  "AMAZON PAY": true,
  "HungerBox": true,
  "Dariya Devi": true,
  "AJMIR BARBHUIYA": true,
  "Kumar Azhagappa": true,
  "BHARATH M V": true,
  "HP PAY DIRECT UPI": true,
  "BRAHMANANDAREDDY KIRANKUMAR": true,
  "GULAB SINGH RAJPUROHIT": true,
  "SANSAR CENTRE": true,
  "MAHAVEER KUMAR PRAJAPAT": true,
  "S DAYANANDA": true,
  "Naresh Kumar": true,
  "Ankush Kumar": true,
  "Rana Ramdev Medical Gunjur": true,
  "Anil Kumar Pal": true,
  "NEEV SUPERMARKET": true,
  "MyHDFC Ac X3300 with HDFC0005493 sent from YONO": false,
  "SHAKIL AHAMAD": true,
  "SHREYA SAGAR": true,
  "Punam MoMo S": true,
  "JAI BHAVANI FANCY AND GIFT CENTER": true,
  "DREAM11": true,
  "MANJAMMA": true,
  "M A ANAGHA": true,
  "UPI": true,
  "Ram Singar Yadav": true,
  "ALL SEASON SUPER MART": true,
  "Balaji pharma": true,
  "CHRONICLES OF HANDI PRIVATE LIMITED": true,
  "Swiggy Ltd": true,
  "ISLAM S K": true,
  "BHANU KHADKA": true,
  "SUDHA R": true,
  "URBAN COMPANY": true,
  "OMARAM": true,
  "NEHATALWAR": true,
  "ANVAYA SILKS": true,
  "SHADOWFAX TECHNOLOGIES PRIVATE LIMITED": true,
  "AKANKSHA INDRA GURU": true,
  "PhonePe": true,
  "SATHIK BATCHA J": true,
  "MAYURA BAKERY AND SWEETS": true,
  "THE BANARAS CAFE": true,
  "Kanti Sweets Private Limited": true,
  "AKSHAYAKALPA FARMS AND FOODS PRIVATE LIMITED": true,
  "Myhdfc ac x3300 with hdfc0005493 sent from yono": false,
  "INR": false,
  "VISHAL KUMAR": false,
  "CHANDRA SHEKHAR MISHRA": false,
  "DEEPAK PRAKASH SRIVASTAV": false,
  "Learning": false,
  "IMPS": false
}'

# Escape the JSON for shell and create the XML content
ESCAPED_JSON=$(echo "$EXCLUSIONS" | sed 's/"/\&quot;/g')

# Create new SharedPreferences XML
cat > /tmp/expense_calculations.xml << EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="group_inclusion_states">$ESCAPED_JSON</string>
</map>
EOF

echo "✅ Updated exclusion states created in /tmp/expense_calculations.xml"
echo "📝 Excluded merchants: MyHDFC, INR, VISHAL KUMAR, CHANDRA SHEKHAR, DEEPAK PRAKASH, Learning, IMPS"