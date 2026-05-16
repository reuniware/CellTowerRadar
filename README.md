# CellTowerRadar Pro - Advanced Cellular Intelligence & Tactical Mapping

CellTowerRadar Pro est une plateforme mobile de surveillance et d'analyse des infrastructures de télécommunications cellulaires. Conçue pour la collecte de métadonnées réseau et l'analyse de l'environnement radioélectrique, l'application permet une visibilité granulaire sur les vecteurs de connectivité mobile (2G/3G/4G/5G).

## 📡 Capacités Techniques & Monitoring

L'application exploite les APIs de bas niveau d'Android pour fournir une analyse en temps réel et historique des signaux environnants.

### ⚡ Intelligence Radio-Fréquence (RF)
*   **Analyse 5G NR (New Radio)** : Identification des architectures **Standalone (SA)** et **Non-Standalone (NSA)**.
*   **Métriques de Signal Avancées** : Monitoring précis du **RSRP**, **RSRQ**, **RSSNR**, et **SS-SINR**.
*   **Analyse du Spectre** : Conversion dynamique des EARFCN/NR-ARFCN en **fréquences réelles (MHz)**.
*   **Estimation d'Infrastructure** : Algorithmes heuristiques pour l'identification du **Constructeur de l'antenne (Vendor)** (Ericsson, Huawei, Nokia, ZTE).
*   **Analyse de Propagation** : Calcul du **Timing Advance (TA)** converti en distance métrique pour l'estimation de proximité de la source.

### 🗺️ Cartographie Tactique & Géolocalisation
*   **Visualisation Geospatiale** : Intégration native de **OpenStreetMap (OSM)** pour le mapping en temps réel des vecteurs détectés.
*   **Positionnement par Corrélation** : Placement automatique des antennes sur la carte via corrélation des coordonnées GNSS au moment du scan.
*   **Audit de Couverture** : Interface visuelle permettant de suivre le trajet de collecte et l'évolution de la puissance du signal sur zone.

### 🛡️ Heuristiques de Sécurité Réseau
*   **Détection d'Anomalies (Anti-Stingray)** : Identification des **Cellules Isolées** (absence de voisines déclarées) et alertes sur les **Downgrades Forcés** suspects vers des protocoles vulnérables (2G/3G).
*   **Monitoring du Handover** : Journalisation des basculements entre cellules pour l'analyse de la stabilité et de la topologie réseau.

### 💾 Persistance & SIGINT (Signal Intelligence)
*   **Foreground Monitoring** : Service persistant avec notification dynamique pour une collecte de données ininterrompue.
*   **Journalisation Historique** : Base de données locale filtrant les vecteurs uniques avec horodatage de précision.
*   **Exportation Multi-Format** : Génération de rapports **CSV** et **KML** pour exploitation avancée dans les outils SIG (Google Earth, QGIS).

### 🚦 Diagnostic d'État Système
*   **Hardware Monitoring** : Dashboard temps réel surveillant le mode avion, l'état du modem, l'économie d'énergie et la disponibilité GNSS.
*   **Feedback Contextuel** : Remplacement des données indisponibles par des états de traitement (`Searching...`, `Limited Neighbor`, `Scanning...`).

## 🛠 Architecture & Sécurité
*   **Framework** : Kotlin / Jetpack Compose / OSMDroid.
*   **Injection de Dépendances** : Hilt pour une modularité de type industriel.
*   **Conformité** : Gestion rigoureuse des flux de permissions conformément aux exigences Android 14+.

## 🚀 Installation & Déploiement
1.  Cloner le dépôt.
2.  Compiler via Android Studio (SDK 36).
3.  Activer la **Localisation (GPS)** pour autoriser le modem à exposer les identifiants techniques.

---
*Ce projet est destiné à l'analyse technique rigoureuse des réseaux mobiles et au monitoring d'infrastructure.*
