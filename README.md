# CellTowerRadar Pro - Advanced Cellular Intelligence Tool

CellTowerRadar Pro est une plateforme mobile de surveillance et d'analyse des infrastructures de télécommunications cellulaires. Conçue pour la collecte de métadonnées réseau et l'analyse de l'environnement radioélectrique, l'application permet une visibilité granulaire sur les vecteurs de connectivité mobile (2G/3G/4G/5G).

## 📡 Capacités Techniques & Monitoring

L'application exploite les APIs de bas niveau d'Android pour fournir une analyse en temps réel et historique des signaux environnants.

### ⚡ Intelligence Radio-Fréquence (RF)
*   **Analyse 5G NR (New Radio)** : Identification des architectures **Standalone (SA)** et **Non-Standalone (NSA)**.
*   **Métriques de Signal Avancées** : Monitoring précis du **RSRP**, **RSRQ**, **RSSNR**, et **SS-SINR**.
*   **Paramètres de Couche Physique** : Extraction du **PCI** (Physical Cell ID), **EARFCN** / **NR-ARFCN**, et identification des **Bandes de fréquences** opérées.
*   **Analyse de Propagation** : Calcul du **Timing Advance (TA)** pour l'estimation de la distance théorique de la source d'émission.

### 🔍 Identification & Vectorisation
*   **Extraction de Métadonnées** : Collecte systématique des identifiants globaux : **MCC** (Mobile Country Code), **MNC** (Mobile Network Code), **LAC/TAC** (Location/Tracking Area Code) et **Cell ID (CID/NCI)**.
*   **Multi-SIM Intelligence** : Capacité de monitoring simultané sur plusieurs interfaces d'abonnement pour une analyse multi-opérateurs en temps réel.
*   **Lookups Externes** : Corrélation de données via des passerelles vers OpenCellID et CellMapper pour la localisation géospatiale des infrastructures.

### 🖥️ Interface & Expérience Utilisateur (UX)
*   **Système d'Audit Interactif** : Interface "Accordéon" permettant de basculer entre une vue synthétique et un audit profond de chaque cellule.
*   **Contrôle d'État Dynamique** : Gestion intelligente des états de scan (Verrouillage des boutons Start/Stop selon l'activité du modem) pour éviter les collisions de requêtes.
*   **Dashboard de Statut** : Retour visuel immédiat sur l'état de la communication avec la couche HAL (Hardware Abstraction Layer) et alertes de configuration (GPS).

### 💾 Persistance & SIGINT (Signal Intelligence)
*   **Foreground Monitoring** : Service persistant permettant une collecte de données en continu, même en cas de verrouillage du terminal.
*   **Journalisation Historique** : Base de données locale des vecteurs uniques rencontrés avec horodatage précis des premières et dernières détections.
*   **Exportation Tactique** : Génération de rapports au format **CSV** pour post-traitement et analyse de trajectoire (War-driving).

## 🛠 Architecture & Sécurité
*   **Framework** : Kotlin / Jetpack Compose.
*   **Injection de Dépendances** : Hilt (Dagger) pour une architecture modulaire et robuste.
*   **Sécurité des Données** : Gestion stricte des permissions `ACCESS_FINE_LOCATION` et `READ_PHONE_STATE` conformément aux exigences de sécurité Android 14+.

## 🚀 Installation & Déploiement
1.  Cloner le dépôt.
2.  Compiler via Android Studio (SDK 36 requis).
3.  Activer impérativement la **Localisation (GPS)** sur le terminal pour autoriser le modem à transmettre les identifiants de cellules.

---
*Ce projet est destiné à l'analyse technique des réseaux mobiles et au monitoring d'infrastructure.*
