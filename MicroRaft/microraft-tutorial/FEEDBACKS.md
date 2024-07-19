# Feedbacks

Actuellement en train d'expérimenter l'insertion de log pour la validation de trace (log des évènements et des modifications de variables) dans le projet `Microraft`, je développe des cas de tests qui exécutent certaines parties du protocole.

## Exécuter les cas de test

Ces cas de test se trouvent dans le projet `microraft-tutorial` et peuvent etre exécutés grâce à la commande `mvnw`, par exemple pour exécuter le cas `VotePhaseValidationTest` : 

`./mvnw clean test -Dtest=io.microraft.tutorial.VotePhaseValidationTest -DfailIfNoTests=false -Ptutorial` 

Vous pourrez trouver ci-dessous certaines remarques sur des problèmes rencontrés

## Feedback général

D'une manière générale, quand on insère des logs de validations de trace dans un projet existant, il est difficile de savoir, lorsque l'on obtient une trace non valide, si c'est l'implémentation du projet qui ne respecte pas la spécification, ou si c'est le log qui est incomplet. 

La meilleure façon de ne pas avoir un log incomplet et de bien notifier tous les événements. Cependant, il sera nécessaire de notifier au fur et à mesure le maximum de changements d'état des variables impliquées dans les évènements pour réduire l'espace d'état et ainsi pouvoir avoir un model checking avec TLC qui se finisse en un temps raisonnable.

## Cas `VotePhaseValidationTest`

Cette première expérience vise à logger les mises à jour de variables et les évènements liés à la phase de vote :

 - Il attend l'élection d'un leader
 - Une fois élu, il transfère le lead sur un autre nœud et de ce fait, provoque une nouvelle élection

### Résultats

Nous obtenons une trace non valide, du fait que dans l'évenement suivant :

`{"clock": 268, "messages": [{"op": "AddToBag", "mode":"map", "path": [], "args": [{"msource": "node1", "mdest": "node1", "mtype": "RequestVoteRequest", "mterm": 4, "mlastLogTerm": 2, "mlastLogIndex": 1}]}], "desc": "RequestVoteRequest", "sender": "dae355c0-d674-4ac5-b2d3-1e0a3026f98e"}`

Les valeurs de `mlastLogTerm` et `mlastLogIndex` ne sont pas correctes. Après quelques remplacements à la main dans la trace, nous savons que les valeurs attendues sont `mlastLogTerm = 0, mlastLogIndex = 0`.

Dans la spécification ces valeurs sont dépendantes de la variable `log` car mises à jour selon : `mlastLogTerm  |-> LastTerm(log[i]),
mlastLogIndex |-> Len(log[i])`.

La non-validité de la trace peut etre dûe à un des deux cas suivant : 

 - Une mauvaise implémentation : car elle met à jour le log entre ces deux phases de votes et ne devrait pas le faire
 - Un log incomplet : car nous n'avons pas logué les évènements mettant à jour la variable `log` (`ClientRequest`, `HandleAppendEntriesRequest`)

__Le deuxième cas est plus vraisemblable.__

## Notes sur le debug

### Modification de la trace à la main

Il est très utile de modifier les valeurs des variables dans la trace à la main, et de lancer un nouveau model checking sur la trace ainsi modifiée. Cela permet de trouver et de connaitre les valeurs potentiellement attendues par la spécification et d'avoir ainsi un indice sur :

 - Quelle(s) variable(s) détient une valeur fausse et pourquoi
 - Quelles actions mettent à jour cette / ces variable(s) et dont potentiellement l'implémentation ne respecte pas la spec.

### Records partiels

Dans notre système de validation de traces actuel, il est nécessaire pour une variable de type record, de logger totalement ou pas du tout sa valeur. Par exemple, si je souhaite ajouter un message dans un bag, je vais devoir logger de telle sorte à obtenir une ligne telle que (par exemple) :

`{"messages": [{"op": "AddToBag", "path": [], "args": [{"msource": "node2", "mdest": "node3", "mtype": "RequestVoteRequest", "mterm": 3, "mlastLogTerm": 0, "mlastLogIndex": 0}]}], "desc": "RequestVoteRequest", "sender": "62838a20-7270-45e2-bb7f-c2d9877f0b31"}`

Ici, j'ai loggé le fait que l'on a ajouté le message `{"msource": "node2", "mdest": "node3", "mtype": "RequestVoteRequest", "mterm": 3, "mlastLogTerm": 0, "mlastLogIndex": 0}` dans le bag `messages`.

Le problème lorsque j'ai une trace invalide à ce niveau, c'est que je ne sais pas quelle est (ou quelles sont) les composantes de ce record pour lesquels la valeur est fausse. Pour répondre à ce problème, nous avons ajouté la possibilité de prendre en compte des records partiels, c'est-à-dire des records pour lesquels des composantes seraient manquantes. Cela pose tout de meme quelques conditions : 

 - L'utilisateur doit préciser explicitement que le record est partiel
 - L'utilisateur doit définir lui meme la fonction (l'opérateur TLC) permettant de choisir comment mapper les valeurs des composantes manquantes en fonction du contexte

#### Exemple de remapping

Ci-dessous un exemple de remapping d'une valeur de record pour `messages` :

```TLA
(* Partial RequestVoteRequest message *)
PartialRequestVoteRequestMessage(val) ==
LET i == val.msource
j == val.mdest
IN
[mtype |-> RequestVoteRequest,
mterm |-> IF "mterm" \in DOMAIN val THEN val.mterm ELSE currentTerm[i],
mlastLogTerm |-> IF "mlastLogTerm" \in DOMAIN val THEN val.mlastLogTerm ELSE LastTerm(log[i]),
mlastLogIndex |-> IF "mlastLogIndex" \in DOMAIN val THEN val.mlastLogIndex ELSE Len(log[i]),
msource |->  i,
mdest |-> j]
```

Dans cet exemple, les composantes `msource` et `mdest` sont requises, tandis que `mterm`, `mlastLogTerm`, `mlastLogIndex` sont optionnelles. Chaque composante optionnelle manquante sera mappée comme elle le serait dans l'action qui utilise ce record, voir ci-dessous :

```
\* Candidate i sends j a RequestVote request.
RequestVote(i, j) ==
    /\ state[i] = Candidate
    /\ j \notin votesResponded[i]
    /\ Send([mtype         |-> RequestVoteRequest,
             mterm         |-> currentTerm[i],
             mlastLogTerm  |-> LastTerm(log[i]),
             mlastLogIndex |-> Len(log[i]),
             msource       |-> i,
             mdest         |-> j])
    /\ UNCHANGED <<serverVars, candidateVars, leaderVars, logVars>>
```

L'utilisateur défini ensuite le contexte dans lequel le record est remappé partiellement :

```
(* Remap some arguments in specific cases before apply operator *)
RAMapArgs(cur, default, op, args, eventName) ==
    (* Handle partial messages records on RequestVoteRequest *)
    (* We need to know event name and check that number of arguments are equal to 1 (one message) *)
    IF eventName = "RequestVoteRequest" /\ Len(args) = 1 THEN
        <<PartialRequestVoteRequestMessage(args[1])>>
    ELSE
        MapArgsBase(cur, default, op, args, eventName)
```

Et précise dans la configuration qu'il réécrit l'opérateur permettant de re-mapper les arguments : 

```    
(* Overwrite map args *)
MapArgs <- RAMapArgs
```

Il s'agit, en quelque sorte, de l'équivalent de :
``` 
MapVariable(...) ==
    IF "myVar" \in DOMAIN t
    THEN myVar' = ApplyUpdates(myVar, "myVar", t)
    ELSE TRUE
    ...
```

Qui permet à l'utilisateur de ne pas logger certaines variables (dont il ne connaitrait pas la valeur par exemple) et de laisser TLC faire une recherche non déterministe sur la valeur de cette variable. Sauf que cette fois-ci, nous omettons de logger des composantes d'une valeur d'un record en laissant TLC appliquer les transformations attendues à ces composantes dans le contexte courant.

### Debugger TLC

TODO write
ENABLED in TraceView

