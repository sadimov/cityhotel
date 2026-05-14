- Continue la vérification du module hébergement 

     * Création clients, sociétés, Quick create société, services (facturés comme des produits
     * ajouter l'annulation d'une réservation au menu contextuel (right click)
	 
     * payer une réservation (partiel, total, excédentaire), paiement individuel d'une nuitée, 
	 d'une charge mise sur la chambre. Repercuter le paiement sur les opérations de comptes et lignes factures correspondantes, 
	 le check out express et mise de facture sur le compte de la société: 
		utilise le dossier /cityhotel/ancienne_implementation/city_frontend/src/app/pay-reservation-client pour implémenter correctement le module paiement
     
	 * corriger modal consultation : afficher prénom et nom du client pas son id.
     * corrige modal modification: afficher prénom et nom du client, pas son id
		Utilise le dossier /cityhotel/ancienne_implementation/city_frontend/src/app/calendar pour corriger le contenu des modals
		Ajouter des icônes parlants et design intuitif aux éléments des modals
		
     * modification individuelle des nuitées : changement du montant d'une ou plusieurs nuitées et répercussions de ça sur les opérations de compte 
			et lignes factures correspondantes
			utilise /cityhotel/ancienne_implementation/city_frontend/src/app/modif-nuitees pour une bonne implémentation de cette modification
      


------------------------------
Retour sur hébergement: 
1. Ajoute la gestion des salles. En plus des chambres, l'hôtel comporte aussi les salles. Une salle est reservée pendant une journée
ou quelques jours pour faire une conférence ou des réunions. La salle a un nom et un numéro. Les salles doivent figurer dans la grille des réservations groupées 
sous les autres types des chambres, Leur réservation doit se faire à partir de la grille. 
2. Dans le modal de modification de la réservation, on doit pouvoir modifier le client lui même (le remplacer par un autre dans la réservation)
Fais tout à la fois sans attente, sans reste.

Règle hébergement: Annulation de réservation se fait seulement par l'admin