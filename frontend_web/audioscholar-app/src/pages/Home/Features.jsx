const Features = () => {
  const features = [
    {
      title: "Smart Recording",
      description: (
        <>
          Record lectures with intelligent<br />
          background processing and offline<br />
          support. Never worry about<br />
          missing content again.
        </>
      )
    },
    {
      title: "AI-Powered Summaries",
      description: "Get instant, well-structured summaries of your recorded lectures using advanced AI technology."
    },
    {
      title: "Cross-Device Sync",
      description: "Access your recordings and summaries seamlessly across all your devices with cloud synchronization."
    }
  ];

  return (
    <section className="py-16 bg-teal-500">
      <div className="container mx-auto px-4">
        <h2 className="text-3xl font-bold text-center text-white mb-12">Why Choose AudioScholar?</h2>
        <div className="grid md:grid-cols-3 gap-8">
          {features.map((feature, index) => (
            <div key={index} className="bg-white p-6 rounded-lg border-2 border-teal-100 hover:border-teal-300 transition-all shadow-sm hover:shadow-md">
              <div className="w-12 h-12 bg-teal-50 rounded-full flex items-center justify-center mb-4">
                <div className="w-8 h-8 bg-teal-500 text-white rounded-full flex items-center justify-center font-medium">
                  {index + 1}
                </div>
              </div>
              <h3 className="text-xl font-semibold text-teal-600 mb-3">{feature.title}</h3>
              <p className="text-gray-600 whitespace-pre-line">
                {typeof feature.description === 'string' ? feature.description : feature.description}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
};

export default Features;