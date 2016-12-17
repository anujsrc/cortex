(ns cortex.nn.protocols
  "Protocols for cortex ML Module implementations"
  (:refer-clojure :exclude [clone]))

#?(:clj (do
          (set! *warn-on-reflection* true)
          (set! *unchecked-math* :warn-on-boxed)))


(defprotocol PModule
  "Protocol for a generic module. All cortex.nn modules must implement this."
  (calc [m input]
    "Performs module calculation, returning an updated module that includes the output and
  any intermediate states computed.")
  (output [m]
    "Returns the calculated output of a module"))


(defprotocol PParameters
  "Protocol for a module that supports parameters. The default implementation returns
an empty parameter vector."
  (parameters [m]
    "Gets the parameters for this module, as a vector.")

  (update-parameters [m parameters]
    "Updates the parameters for this module to the given parameter values.
     Clears the accumulated gradient and returns the updated module"))


(defprotocol PParameterCount
  "Protocol for computing the parameter count. The default implementation just calls
count on the parameter vector.."
  (parameter-count [m]
    "Gets the number of parameters for this module, as a long value."))


(defprotocol PGradient
  "Protocol for a module that supports accumulated gradients for optimisation.
This vector should be exactly the
  same length as the parameter vector.
  The default implementation returns an empty gradient vector."
  (gradient [m]
    "Gets the accumulated gradient for this module, as a vector."))

(defprotocol PLossGradientFunction
  "Protocol to return a function that computes the gradient of the loss function for a module."
  (loss-gradient-fn [m]
    "Gets a fn [output traget] that computes the loss gradient for a module. May return nil to
  indicate that no loss function is specified."))

(defprotocol PNeuralTraining
  "Protocol for modules that can be trained with forward / back propagation."
  (forward [this input]
    "Run a forward computation pass and return the updated module. output will be
    available for later retrieval. Input and intermediate states will be stored for
    future backward pass usage.

   During training prepare-forward must be called first to ensure that any necessary
   pre-training calculations are performed. forward itself should be deterministic,
   but prepare-forward may modify stochastic state (e.g. dropout).")

  (backward [this input output-gradient]
    "Back propagate errors through the module with respect to the input.  Returns the
  module with input-gradient set (gradient at the inputs). Input must be the same
  as used in the corresponding forward pass.")

  (input-gradient [this]
    "Gets the computed input gradients for a module. Assumes the backward pass has been run."))


(defprotocol PNeuralTrainingOptional
  "Some layers will want to do things before the forward pass like update random number buffers.
  This allows things like dropout to play well with gradient checking in that prepare-forward can
  be called once but forward can be called multiple times (normally 1 + 2 * n-params).

  prepare-forward should return its parameter unmodified if no preparation is required."
  (prepare-forward [this]))



;;Default implementation because most things do not need this implemented
(extend-protocol PNeuralTrainingOptional
  Object
  (prepare-forward [this] this))


(defprotocol PTraining
  "Protocol for modules that can be trained input / output pairs."
  (train [this input output]
    "Trains the module to produce the given input / output. Accumulates gradients as necessary.
  Intended for use with update-parameters after completion of a (mini-)batch."))


(defprotocol PGradientOptimiser
  "A gradient optimiser is an abstraction for objects that update parameters based on
gradient observations.
  Gradient optimisers typically contain relating to previous observations, momentum etc."
  (compute-parameters
    [optimiser gradient parameters]
    "Computes updated parameters using the given average gradient. Returns the updated gradient
optimiser.
  Users can then call `parameters` on this object to get the updated parameters"))


(defprotocol PIntrospection
  "Protocol for objects that can return information about their internal
  state."
  (get-state [this]
    "Return a (possibly lazy) map containing information about this
  object's internal state. This map is not expected to contain
  information about parameters, value, or gradient, if applicable."))


(defprotocol PLossFunction
  "A function that calculates loss of a vector output vs. a target value"
  (loss [this v target]
    "Computes the loss for a value v against a target")
  (loss-gradient [this v target]
    "Computes the gradient of the loss with respect to v"))


(defprotocol PMultiLayer
  "The generalized network takes vectors of input and products vectors of outputs.
  This protocol is used for access and has implementations that specialize to the normal
  (single input,output) layer types.  This shouldn't be confused with batching which
  could possibly also produce vectors of input, this is designed for branching networks
  which get disparate possibly differently shaped inputs and produce disparate and possibly
  differently shaped outputs."
  (multi-input-size [layer])
  (multi-output-size [layer])
  (multi-calc [m input-vec])
  (multi-forward [m input-vec])
  (multi-backward [m input-vec output-gradient-vec])
  (multi-output [m] "Returns a vector of outputs for module")
  (multi-input-gradient [this] "Returns vector of input gradients for the module"))


;; Specialise the multi player such that normal layers (that take single input,outputs)
;; do not have to change.
(extend-protocol PMultiLayer
  Object
  (multi-input-size [layer] [(input-size layer)])
  (multi-output-size [layer] [(output-size layer)])
  (multi-calc [m input-vec] (calc m (first input-vec)))
  (multi-forward [m input-vec] (forward m (first input-vec)))
  (multi-backward [m input-vec output-gradient-vec]
    (backward m (first input-vec)
              (first output-gradient-vec)))
  (multi-output [m] [(output m)])
  (multi-input-gradient [m] [(input-gradient m)]))
