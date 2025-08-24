#!/bin/bash

echo "🔧 Creating comprehensive exclusion list for large transactions..."

# Create updated exclusion JSON with all problematic merchants excluded
cat > /tmp/comprehensive_exclusions.json << 'EOF'
{
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
  "MyHDFC Ac X3300 with HDFC0005493 sent from YONO": false,
  "Myhdfc ac x3300 with hdfc0005493 sent from yono": false,
  "MYHDFC AC X3300 WITH HDFC0005493 SENT FROM YONO": false,
  "INR": false,
  "VISHAL KUMAR": false,
  "CHANDRA SHEKHAR MISHRA": false,
  "DEEPAK PRAKASH SRIVASTAV": false,
  "Learning": false,
  "LEARNING": false,
  "IMPS": false,
  "linked to mobile 8XXXXXX832": false,
  "LINKED TO MOBILE 8XXXXXX832": false
}
EOF

echo "✅ Created comprehensive exclusion list"
echo "📝 Excluded merchants (set to false):"
echo "   - MyHDFC (all variations)"
echo "   - INR"
echo "   - VISHAL KUMAR"
echo "   - CHANDRA SHEKHAR MISHRA" 
echo "   - DEEPAK PRAKASH SRIVASTAV"
echo "   - Learning (all variations)"
echo "   - IMPS"
echo "   - linked to mobile (bank transfers)"

# Create the XML with escaped JSON
ESCAPED_JSON=$(cat /tmp/comprehensive_exclusions.json | sed 's/"/\&quot;/g' | tr -d '\n')

cat > /tmp/expense_calculations_fixed.xml << EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="group_inclusion_states">$ESCAPED_JSON</string>
</map>
EOF

echo "✅ Created fixed SharedPreferences XML at /tmp/expense_calculations_fixed.xml"