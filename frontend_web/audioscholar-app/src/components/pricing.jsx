const Pricing = () => {
  const plans = [
    {
      name: "Free",
      price: "$0",
      features: [
        "A basic recording structure",
        "A single printer summary",
        "A cloud storage story"
      ],
      cta: "Get Started"
    },
    {
      name: "Premium",
      price: "$9.99",
      features: [
        "A telephone recording",
        "A telephone numbering",
        "A document processing",
        "Clouds synchronization",
        "Advanced AI literature"
      ],
      cta: "Buy Now",
      featured: true
    }
  ];

  return (
    <section className="py-16 bg-teal-500">
      <div className="container mx-auto px-4">
        <h2 className="text-3xl font-bold text-center text-white mb-4">Pricing Plans</h2>
        <p className="text-center text-white/80 mb-12">Inventory • Account</p>
        
        <div className="grid md:grid-cols-2 gap-8 max-w-4xl mx-auto">
          {plans.map((plan, index) => (
            <div 
              key={index} 
              className={`p-8 rounded-lg shadow-sm border-2 ${plan.featured ? 'border-teal-500 bg-white' : 'border-gray-200 bg-white'}`}
            >
              {plan.featured && (
                <div className="bg-teal-500 text-white text-sm font-medium py-1 px-3 rounded-full inline-block mb-4">
                  Popular
                </div>
              )}
              <h3 className="text-2xl font-bold mb-1">{plan.name} <span className="text-teal-600">{plan.price}</span></h3>
              <ul className="my-6 space-y-3">
                {plan.features.map((feature, i) => (
                  <li key={i} className="flex items-start">
                    <svg className="h-5 w-5 mr-2 text-teal-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                    </svg>
                    {feature}
                  </li>
                ))}
              </ul>
              <button className={`w-full py-3 px-4 rounded-md font-medium transition-colors ${plan.featured ? 'bg-teal-500 hover:bg-teal-600 text-white' : 'bg-gray-100 hover:bg-gray-200 text-gray-800'}`}>
                {plan.cta}
              </button>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Pricing;